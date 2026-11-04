#ifndef STARDROID_HD2_RUNTIME_H
#define STARDROID_HD2_RUNTIME_H

#include "geometry.h"
#include <atomic>
#include <cerrno>
#include <fcntl.h>
#include <memory>
#include <mutex>
#include <sys/stat.h>
#include <unistd.h>

namespace stardroid { namespace remastered { namespace live {

// Direct images.dat mappings verified against the original GRP and HD2 samples.
struct Body {
    unsigned id, frames, grp_width, grp_height;
    const char* filename;
};
inline constexpr Body bodies[] = {
    {0,   85, 48, 48, "main_000.anim"}, // Scourge
    {5,  204, 48, 48, "main_005.anim"}, // Broodling
    {13, 289, 80, 80, "main_013.anim"}, // Defiler
    {122,415, 96, 96, "main_122.anim"}, // Dragoon
    {228,229, 64, 64, "main_228.anim"}  // Ghost
};
constexpr size_t body_count = sizeof(bodies) / sizeof(bodies[0]);
constexpr size_t max_file_bytes = 16u * 1024u * 1024u;
constexpr size_t max_pack_bytes = 32u * 1024u * 1024u;

// One entry per original HD2 atlas texel. The mask is stored next to its
// diffuse RGBA so overlapping animation frame crops can share one allocation.
struct Texel {
    uint8_t r, g, b, a, team;
};
static_assert(sizeof(Texel) == 5, "HD2 atlas byte budget assumes packed samples");
struct Pose {
    int left = 0, top = 0, width = 0, height = 0, stride = 0;
    const Texel* first = nullptr;
    bool empty() const { return first == nullptr; }
};
struct Pack {
    std::array<std::vector<Texel>, body_count> atlases;
    std::array<std::vector<Pose>, body_count> poses;
    size_t count = 0, bytes = 0;
    Pack() = default;
    Pack(const Pack&) = delete;
    Pack& operator=(const Pack&) = delete;
};
inline std::shared_ptr<const Pack> active_pack;
inline std::mutex loader_mutex;

inline void charge(size_t& used, size_t count, size_t element) {
    if (count > (max_pack_bytes - used) / element) throw FormatError("HD2 sprite cache exceeds budget");
    used += count * element;
}
inline void cache_body(Pack& pack, size_t slot, const Anim& anim) {
    const auto decoded = anim.decode_sprite();
    const auto& diffuse = decoded.diffuse();
    const auto* mask = decoded.teamcolor();
    const size_t pixels = size_t(diffuse.width) * diffuse.height;
    charge(pack.bytes, pixels, sizeof(Texel));
    charge(pack.bytes, anim.frames().size(), sizeof(Pose));
    auto& atlas = pack.atlases[slot];
    atlas.resize(pixels);
    for (uint32_t y = 0; y < diffuse.height; ++y) {
        for (uint32_t x = 0; x < diffuse.width; ++x) {
            const size_t pixel = size_t(y) * diffuse.width + x;
            const auto* rgba = &diffuse.rgba[pixel * 4];
            uint8_t amount = 0;
            if (mask && x < mask->width && y < mask->height)
                amount = mask->rgba[(size_t(y) * mask->width + x) * 4];
            atlas[pixel] = {rgba[0], rgba[1], rgba[2], rgba[3], amount};
        }
    }
    auto& poses = pack.poses[slot];
    poses.reserve(anim.frames().size());
    for (size_t index = 0; index < anim.frames().size(); ++index) {
        const auto crop = decoded.frame_bounds(index);
        if (!crop.width || !crop.height) { poses.emplace_back(); continue; }
        const auto rect = place_frame(anim, index, 0, 0, false);
        poses.push_back({rect.left, rect.top, int(crop.width), int(crop.height), int(diffuse.width),
            &atlas[size_t(crop.tex_y) * diffuse.width + crop.tex_x]});
    }
}

// Opening files relative to the selected immutable import generation avoids
// following symlinks and ensures the size bound applies to the open descriptor.
struct Descriptor {
    int fd;
    explicit Descriptor(int value) : fd(value) {}
    ~Descriptor() { if (fd >= 0) ::close(fd); }
    Descriptor(const Descriptor&) = delete;
    Descriptor& operator=(const Descriptor&) = delete;
};
inline std::shared_ptr<Pack> read_pack(const std::string& directory) {
    auto pack = std::make_shared<Pack>();
    if (directory.empty()) return pack;
    Descriptor root(::open(directory.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
    if (root.fd < 0) throw FormatError("Cannot open HD2 sprite directory");
    Limits limits;
    limits.max_file_bytes = max_file_bytes;
    limits.max_frames = 512;
    limits.max_dimension = 4096;
    limits.max_decoded_pixels = 2u * 1024u * 1024u;
    limits.max_working_pixels = 4u * 1024u * 1024u;
    for (size_t i = 0; i < body_count; ++i) {
        const auto& body = bodies[i];
        Descriptor file(::openat(root.fd, body.filename, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK));
        if (file.fd < 0) {
            if (errno == ENOENT) continue;
            throw FormatError("Cannot open HD2 sprite");
        }
        struct stat st{};
        if (fstat(file.fd, &st) || !S_ISREG(st.st_mode) || st.st_size <= 0 || uint64_t(st.st_size) > max_file_bytes)
            throw FormatError("Invalid HD2 sprite file");
        std::vector<uint8_t> input(size_t(st.st_size));
        size_t done = 0;
        while (done < input.size()) {
            const ssize_t n = ::read(file.fd, input.data() + done, input.size() - done);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) throw FormatError("Truncated HD2 sprite");
            done += size_t(n);
        }
        auto anim = Anim::parse(std::move(input), limits);
        if (anim.scale() != 2 || anim.frames().size() != body.frames ||
            !anim.canvas_width_hd() || !anim.canvas_height_hd()) throw FormatError("Incompatible HD2 sprite");
        cache_body(*pack, i, anim);
        ++pack->count;
    }
    return pack;
}
inline int configure(const std::string& directory, bool activate) {
    std::lock_guard<std::mutex> guard(loader_mutex);
    try {
        std::shared_ptr<const Pack> replacement = read_pack(directory);
        const int count = int(replacement->count);
        if (activate) {
            if (!count) replacement.reset();
            std::atomic_store(&active_pack, std::move(replacement));
        }
        return count;
    } catch (const std::exception&) {
        // A bad replacement must not drop a previously working import.
        return -1;
    }
}

// One lookup per quantized output color. Built when installing the tileset or
// enabling the pack; subsequent image draws only read this 32 KiB table.
struct Palette {
    std::array<uint8_t, 32 * 32 * 32> nearest{};
    bool ready = false;
    void prepare(const uint8_t* wpe) {
        for (int key = 0; key < int(nearest.size()); ++key) {
            const int r = (int((key >> 10) & 31) * 255 + 15) / 31;
            const int g = (int((key >> 5) & 31) * 255 + 15) / 31;
            const int b = (int(key & 31) * 255 + 15) / 31;
            int best = std::numeric_limits<int>::max();
            for (unsigned i = 0; i < 256; ++i) {
                const int dr = r - wpe[i * 4], dg = g - wpe[i * 4 + 1], db = b - wpe[i * 4 + 2];
                const int distance = dr * dr + dg * dg + db * db;
                if (distance < best) { best = distance; nearest[key] = uint8_t(i); }
            }
        }
        ready = true;
    }
    uint8_t index(unsigned r, unsigned g, unsigned b) const {
        return nearest[(std::min(r,255u) >> 3) * 1024 + (std::min(g,255u) >> 3) * 32 + (std::min(b,255u) >> 3)];
    }
};
// The target is measured in HD2 pixels; centers are camera-relative world
// coordinates. Clip in 64-bit space before narrowing to a visible rectangle.
// Callback receives straight, unquantized RGBA at each covered target texel.
template<class WritePixel>
inline void for_each_texel(const Pose& pose, int cx, int cy, bool flipped, const uint8_t* tint,
                           int width2, int height2, WritePixel&& write) {
    if (pose.empty() || width2 <= 0 || height2 <= 0) return;
    const int64_t left = int64_t(cx) * 2 + (flipped ? -int64_t(pose.left) - pose.width : pose.left);
    const int64_t top = int64_t(cy) * 2 + pose.top;
    if (left >= width2 || top >= height2 || left + pose.width <= 0 || top + pose.height <= 0) return;
    const int x0 = int(std::max<int64_t>(0, -left));
    const int y0 = int(std::max<int64_t>(0, -top));
    const int x1 = int(std::min<int64_t>(pose.width, width2 - left));
    const int y1 = int(std::min<int64_t>(pose.height, height2 - top));
    const auto tinted = [](uint8_t channel, uint8_t team, uint8_t color) {
        const unsigned factor = 65025u - unsigned(team) * (255u - color);
        return uint8_t((channel * factor + 32512u) / 65025u);
    };
    for (int y = y0; y < y1; ++y) {
        const auto* row = pose.first + size_t(y) * pose.stride;
        for (int x = x0; x < x1; ++x) {
            const auto& t = row[flipped ? pose.width - 1 - x : x];
            if (!t.a) continue;
            if (t.team) {
                write(int(left+x), int(top+y), tinted(t.r, t.team, tint[0]),
                      tinted(t.g, t.team, tint[1]), tinted(t.b, t.team, tint[2]), t.a);
            } else write(int(left+x), int(top+y), t.r, t.g, t.b, t.a);
        }
    }
}

// Used only by a classic-resolution destination (for example when the 2x
// target exceeds the display memory cap). Preserve preview1's box filter
// and its alpha-weighted team mask, without a second cache or per-draw buffer.
inline void blit_reduced(const Pose& pose, int cx, int cy, bool flipped, const uint8_t* tint,
                         const Palette& palette, const uint8_t* wpe, uint8_t* dst, size_t pitch,
                         int width, int height) {
    if (pose.empty() || width <= 0 || height <= 0) return;
    const int64_t left = int64_t(cx) * 2 + (flipped ? -int64_t(pose.left) - pose.width : pose.left);
    const int64_t top = int64_t(cy) * 2 + pose.top;
    if (left >= int64_t(width) * 2 || top >= int64_t(height) * 2 || left + pose.width <= 0 || top + pose.height <= 0) return;
    const int x0 = int(std::max<int64_t>(0, left / 2));
    const int y0 = int(std::max<int64_t>(0, top / 2));
    const int x1 = int(std::min<int64_t>(width, (left + pose.width + 1) / 2));
    const int y1 = int(std::min<int64_t>(height, (top + pose.height + 1) / 2));
    for (int y = y0; y < y1; ++y) {
        const int fy = int(int64_t(y) * 2 - top);
        for (int x = x0; x < x1; ++x) {
            const int fx = int(int64_t(x) * 2 - left);
            unsigned alpha = 0, color[3]{}, masked[3]{};
            for (int sy = std::max(0, fy); sy < std::min(pose.height, fy + 2); ++sy) {
                const auto* row = pose.first + size_t(sy) * pose.stride;
                for (int sx = std::max(0, fx); sx < std::min(pose.width, fx + 2); ++sx) {
                    const auto& t = row[flipped ? pose.width - 1 - sx : sx];
                    alpha += t.a;
                    const unsigned weighted[3] = {unsigned(t.r) * t.a, unsigned(t.g) * t.a, unsigned(t.b) * t.a};
                    for (unsigned c = 0; c < 3; ++c) {
                        color[c] += weighted[c];
                        masked[c] += weighted[c] * t.team;
                    }
                }
            }
            alpha = (alpha + 2) / 4;
            if (!alpha) continue;
            auto& index = dst[size_t(y) * pitch + x];
            const auto* background = wpe + size_t(index) * 4;
            for (unsigned c = 0; c < 3; ++c) {
                const unsigned foreground = (color[c] + 510) / 1020;
                const unsigned mask = (masked[c] + 130050) / 260100;
                color[c] = foreground - (mask * (255 - tint[c]) + 127) / 255
                        + (unsigned(background[c]) * (255 - alpha) + 127) / 255;
            }
            index = palette.index(color[0], color[1], color[2]);
        }
    }
}

}}} // namespace stardroid::remastered::live
#endif
