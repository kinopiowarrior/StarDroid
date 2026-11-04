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

// Filter four HD2 texels into one battlefield pixel before the game starts.
// Both RGB terms include coverage. Subtracting the masked part times (1 -
// team color) tints only the marked material, even along transparent edges.
struct Texel {
    std::array<uint8_t, 3> color{}, masked{};
    uint8_t alpha = 0;
};
struct Pose {
    int left = 0, top = 0, width = 0, height = 0;
    std::vector<Texel> pixels;
};
struct Pack {
    std::array<std::vector<Pose>, body_count> poses;
    size_t count = 0, bytes = 0;
};
inline std::shared_ptr<const Pack> active_pack;
inline std::mutex loader_mutex;

inline int floor_half(int n) { return n >= 0 ? n / 2 : -((-n + 1) / 2); }
inline void charge(size_t& used, size_t count, size_t element) {
    if (count > (max_pack_bytes - used) / element) throw FormatError("HD2 sprite cache exceeds budget");
    used += count * element;
}
inline Pose bake(const Anim& anim, const DecodedSprite& sprite, size_t index, size_t& bytes) {
    const auto crop = sprite.frame_bounds(index);
    Pose pose;
    if (!crop.width || !crop.height) return pose;
    const auto rect = place_frame(anim, index, 0, 0, false);
    pose.left = floor_half(rect.left);
    pose.top = floor_half(rect.top);
    pose.width = -floor_half(-rect.left - int(rect.width)) - pose.left;
    pose.height = -floor_half(-rect.top - int(rect.height)) - pose.top;
    const size_t count = size_t(pose.width) * pose.height;
    charge(bytes, count, sizeof(Texel));
    pose.pixels.resize(count);
    const auto& diffuse = sprite.diffuse();
    const Image* mask = sprite.teamcolor();
    for (int dest_y = 0; dest_y < pose.height; ++dest_y) {
        for (int x = 0; x < pose.width; ++x) {
            const int source_x = 2 * (pose.left + x) - rect.left;
            const int source_y = 2 * (pose.top + dest_y) - rect.top;
            unsigned alpha = 0, rgb[3]{}, masked[3]{};
            for (int sy = std::max(source_y, 0); sy < std::min(source_y + 2, int(rect.height)); ++sy) {
                for (int sx = std::max(source_x, 0); sx < std::min(source_x + 2, int(rect.width)); ++sx) {
                    const size_t tx = crop.tex_x + sx, ty = crop.tex_y + sy;
                    const auto* pixel = &diffuse.rgba[(ty * diffuse.width + tx) * 4];
                    const unsigned amount = mask ? mask->rgba[(ty * mask->width + tx) * 4] : 0;
                    alpha += pixel[3];
                    for (unsigned c = 0; c < 3; ++c) {
                        const unsigned value = unsigned(pixel[c]) * pixel[3];
                        rgb[c] += value;
                        masked[c] += value * amount;
                    }
                }
            }
            auto& out = pose.pixels[size_t(dest_y) * pose.width + x];
            out.alpha = uint8_t((alpha + 2) / 4);
            for (unsigned c = 0; c < 3; ++c) {
                out.color[c] = uint8_t((rgb[c] + 510) / 1020);
                out.masked[c] = uint8_t((masked[c] + 130050) / 260100);
            }
        }
    }
    return pose;
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
        auto sprite = anim.decode_sprite();
        charge(pack->bytes, body.frames, sizeof(Pose));
        auto& poses = pack->poses[i];
        poses.reserve(body.frames);
        for (size_t f = 0; f < body.frames; ++f) poses.push_back(bake(anim, sprite, f, pack->bytes));
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
inline void blit(const Pose& pose, int cx, int cy, bool flip, const uint8_t* tint,
                 const Palette& palette, const uint8_t* wpe, uint8_t* dst, size_t pitch,
                 int screen_width, int screen_height) {
    const int left = cx + (flip ? -pose.left - pose.width : pose.left), top = cy + pose.top;
    for (int y = std::max(0,-top); y < std::min(pose.height,screen_height-top); ++y) {
        for (int x = std::max(0,-left); x < std::min(pose.width,screen_width-left); ++x) {
            const auto& p = pose.pixels[size_t(y) * pose.width + (flip ? pose.width-1-x : x)];
            if (!p.alpha) continue;
            auto& target = dst[size_t(top+y) * pitch + left+x];
            const auto* bg = &wpe[size_t(target) * 4];
            unsigned rgb[3];
            for (unsigned c = 0; c < 3; ++c) {
                const unsigned attenuate = (unsigned(p.masked[c]) * (255-tint[c]) + 127) / 255;
                rgb[c] = p.color[c] - attenuate + (unsigned(bg[c]) * (255-p.alpha) + 127) / 255;
            }
            target = palette.index(rgb[0], rgb[1], rgb[2]);
        }
    }
}

}}} // namespace stardroid::remastered::live
#endif
