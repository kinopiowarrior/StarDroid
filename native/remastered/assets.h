#ifndef STARDROID_REMASTERED_ASSETS_H
#define STARDROID_REMASTERED_ASSETS_H

// Independently implemented from documented format facts. See
// docs/REMASTERED-GRAPHICS.md for reference revisions and current scope.
// This is an offline asset reader, not a replacement for the game renderer.

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <optional>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace stardroid { namespace remastered {

class FormatError : public std::runtime_error {
public:
    explicit FormatError(const std::string& message) : std::runtime_error(message) {}
};

struct Limits {
    size_t max_file_bytes = 128u * 1024u * 1024u;
    uint64_t max_decoded_pixels = 16u * 1024u * 1024u;
    uint64_t max_working_pixels = 32u * 1024u * 1024u;
    uint32_t max_dimension = 16384;
    uint32_t max_frames = 8192;
};

// Pixels are straight-alpha RGBA, never premultiplied. A BMP mask is exported
// as opaque grayscale RGBA; it is a one-byte mask, not a Windows BMP image.
struct Image {
    uint32_t width = 0, height = 0;
    std::vector<uint8_t> rgba;
};

// Raw ANIM geometry uses HD (4x) coordinates in both HD and HD2 containers.
struct Frame {
    uint16_t tex_x = 0, tex_y = 0;
    int16_t x_off = 0, y_off = 0;
    uint16_t width = 0, height = 0;
    uint32_t flags = 0;
};

struct Texture {
    uint32_t offset = 0, size = 0;
    uint16_t width = 0, height = 0;
    bool present() const { return offset != 0; }
};

// Texture-pixel coordinates, with signed offsets rounded toward minus infinity.
// Canvas dimensions are only container metadata, not a verified gameplay anchor.
struct FrameRegion {
    uint32_t tex_x = 0, tex_y = 0, width = 0, height = 0;
    int32_t x_off = 0, y_off = 0;
};

class Anim {
public:
    static Anim parse(std::vector<uint8_t> data, Limits limits = {}) {
        if (data.size() > limits.max_file_bytes) throw FormatError("ANIM exceeds input byte limit");
        Anim out(std::move(data), limits);
        out.read();
        return out;
    }

    uint8_t scale() const { return scale_; }
    uint16_t canvas_width_hd() const { return canvas_width_; }
    uint16_t canvas_height_hd() const { return canvas_height_; }
    const std::vector<Frame>& frames() const { return frames_; }
    const std::vector<std::string>& layer_names() const { return names_; }
    const std::vector<Texture>& textures() const { return textures_; }

    FrameRegion frame_bounds(size_t index) const {
        if (index >= frames_.size()) throw FormatError("ANIM frame index out of range");
        const Frame& f = frames_[index];
        const uint32_t div = scale_ == 2 ? 2 : 1;
        return {f.tex_x / div, f.tex_y / div, f.width / div, f.height / div,
            floor_div(f.x_off, div), floor_div(f.y_off, div)};
    }

    Image decode_layer(const std::string& name) const {
        return decode_texture(layer_index(name));
    }

    // Non-RTL diffuse preview. The optional team color multiplies diffuse RGB
    // under the teamcolor red-channel mask, retaining the original alpha.
    Image frame_image(size_t index,
        std::optional<std::array<uint8_t, 3>> team_color = std::nullopt) const {
        const FrameRegion region = frame_bounds(index);
        if (region.width == 0 || region.height == 0) return {};
        const size_t diffuse_id = layer_index("diffuse");
        const Texture& dt = textures_[diffuse_id];
        size_t team_id = names_.size();
        if (team_color) {
            for (size_t i = 0; i < names_.size(); ++i)
                if (names_[i] == "teamcolor" && textures_[i].present()) team_id = i;
        }
        uint64_t working = uint64_t(dt.width) * dt.height + uint64_t(region.width) * region.height;
        if (team_id < names_.size()) working += uint64_t(textures_[team_id].width) * textures_[team_id].height;
        if (working > limits_.max_working_pixels) throw FormatError("ANIM preview exceeds working pixel limit");
        const Image diffuse = decode_texture(diffuse_id);
        Image team;
        if (team_id < names_.size()) team = decode_texture(team_id);
        Image result = allocate(region.width, region.height);
        for (uint32_t y = 0; y < region.height; ++y) {
            for (uint32_t x = 0; x < region.width; ++x) {
                const size_t src = (size_t(region.tex_y + y) * diffuse.width + region.tex_x + x) * 4;
                const size_t dst = (size_t(y) * result.width + x) * 4;
                for (unsigned c = 0; c < 4; ++c) result.rgba[dst + c] = diffuse.rgba[src + c];
                if (team_id < names_.size()) {
                    const size_t mask_at = (size_t(region.tex_y + y) * team.width + region.tex_x + x) * 4;
                    const unsigned mask = team.rgba[mask_at];
                    for (unsigned c = 0; c < 3; ++c) {
                        const unsigned original = diffuse.rgba[src + c];
                        const unsigned factor = 255 * (255 - mask) + (*team_color)[c] * mask;
                        // Quantize once after mix(diffuse, diffuse * team, mask).
                        result.rgba[dst + c] = uint8_t((original * factor + 32512) / 65025);
                    }
                }
            }
        }
        return result;
    }

private:
    std::vector<uint8_t> data_;
    Limits limits_;
    uint8_t scale_ = 0;
    uint16_t canvas_width_ = 0, canvas_height_ = 0;
    std::vector<std::string> names_;
    std::vector<Texture> textures_;
    std::vector<Frame> frames_;

    Anim(std::vector<uint8_t> data, Limits limits) : data_(std::move(data)), limits_(limits) {}

    void span(uint64_t offset, uint64_t size, const char* context) const {
        if (offset > data_.size() || size > uint64_t(data_.size()) - offset)
            throw FormatError(std::string(context) + " is truncated or outside the file");
    }
    uint16_t u16(size_t at) const {
        span(at, 2, "16-bit field");
        return uint16_t(data_[at]) | (uint16_t(data_[at + 1]) << 8);
    }
    int16_t i16(size_t at) const {
        const uint16_t u = u16(at);
        return u <= 32767 ? int16_t(u) : int16_t(int32_t(u) - 65536);
    }
    uint32_t u32(size_t at) const {
        span(at, 4, "32-bit field");
        return uint32_t(data_[at]) | (uint32_t(data_[at + 1]) << 8) |
            (uint32_t(data_[at + 2]) << 16) | (uint32_t(data_[at + 3]) << 24);
    }
    static int32_t floor_div(int32_t value, uint32_t div) {
        return value >= 0 ? value / int32_t(div) : -int32_t((uint32_t(-value) + div - 1) / div);
    }
    void dimensions(uint32_t width, uint32_t height) const {
        if (!width || !height || width > limits_.max_dimension || height > limits_.max_dimension)
            throw FormatError("Invalid or oversized texture dimensions");
        const uint64_t pixels = uint64_t(width) * height;
        if (pixels > limits_.max_decoded_pixels || pixels > std::numeric_limits<size_t>::max() / 4)
            throw FormatError("Texture exceeds decoded pixel limit");
    }
    Image allocate(uint32_t width, uint32_t height) const {
        dimensions(width, height);
        return {width, height, std::vector<uint8_t>(size_t(width) * height * 4, 0)};
    }
    size_t layer_index(const std::string& name) const {
        for (size_t i = 0; i < names_.size(); ++i) {
            if (names_[i] == name) {
                if (!textures_[i].present()) throw FormatError("ANIM layer is absent: " + name);
                return i;
            }
        }
        throw FormatError("ANIM layer not found: " + name);
    }
    static bool overlaps(uint64_t a, uint64_t alen, uint64_t b, uint64_t blen) {
        return alen && blen && a < b + blen && b < a + alen;
    }

    void read() {
        span(0, 0x14c + 12, "ANIM header");
        if (u32(0) != 0x4d494e41u) throw FormatError("Not an ANIM file");
        scale_ = data_[4];
        if (data_[5] != 2 || (scale_ != 2 && scale_ != 4))
            throw FormatError("Unsupported ANIM variant; only single-entry HD2/HD is supported");
        const uint16_t layers = u16(8), entries = u16(10);
        if (layers == 0 || layers > 10) throw FormatError("Invalid ANIM layer count");
        if (entries != 1) throw FormatError("Unsupported ANIM entry count");
        const uint64_t metadata_end = 0x14c + 12 + uint64_t(layers) * 12;
        span(0, metadata_end, "ANIM texture descriptors");
        for (size_t i = 0; i < layers; ++i) {
            std::string name;
            for (size_t j = 0; j < 32 && data_[12 + i * 32 + j]; ++j) {
                const uint8_t value = data_[12 + i * 32 + j];
                if (value < 32 || value > 126) throw FormatError("Invalid ANIM layer name");
                name.push_back(char(value));
            }
            if (name.empty() || std::find(names_.begin(), names_.end(), name) != names_.end())
                throw FormatError("Empty or duplicate ANIM layer name");
            names_.push_back(name);
        }
        const uint16_t count = u16(0x14c);
        if (!count || count > limits_.max_frames) throw FormatError("Invalid or oversized ANIM frame count");
        if (u16(0x14e) != 0xffff) throw FormatError("ANIM reference entries are not supported");
        canvas_width_ = u16(0x150);
        canvas_height_ = u16(0x152);
        const uint32_t frame_offset = u32(0x154);
        const uint64_t frame_bytes = uint64_t(count) * 16;
        if (frame_offset < metadata_end) throw FormatError("ANIM frame table overlaps metadata");
        span(frame_offset, frame_bytes, "ANIM frame table");
        for (size_t i = 0; i < layers; ++i) {
            const size_t at = 0x158 + i * 12;
            Texture tex{u32(at), u32(at + 4), u16(at + 8), u16(at + 10)};
            if (tex.present()) {
                dimensions(tex.width, tex.height);
                if (tex.size < 4 || tex.offset < metadata_end)
                    throw FormatError("Invalid ANIM texture span");
                span(tex.offset, tex.size, "ANIM texture");
                if (overlaps(tex.offset, tex.size, frame_offset, frame_bytes))
                    throw FormatError("ANIM texture overlaps frame table");
                for (const Texture& other : textures_)
                    if (other.present() && overlaps(tex.offset, tex.size, other.offset, other.size))
                        throw FormatError("Overlapping ANIM texture payloads are not supported");
            } else if (tex.size != 0) {
                throw FormatError("Absent ANIM texture has nonzero size");
            }
            textures_.push_back(tex);
        }
        for (size_t i = 0; i < count; ++i) {
            const size_t at = size_t(frame_offset) + i * 16;
            frames_.push_back({u16(at), u16(at + 2), i16(at + 4), i16(at + 6),
                u16(at + 8), u16(at + 10), u32(at + 12)});
            const FrameRegion r = frame_bounds(i);
            if (!r.width || !r.height) continue;
            for (const Texture& tex : textures_)
                if (tex.present() && (uint64_t(r.tex_x) + r.width > tex.width ||
                    uint64_t(r.tex_y) + r.height > tex.height))
                    throw FormatError("ANIM frame crop is outside a texture");
        }
    }

    static std::array<uint8_t, 4> color565(uint16_t value) {
        const unsigned r = (value >> 11) & 31, g = (value >> 5) & 63, b = value & 31;
        return {uint8_t((r << 3) | (r >> 2)), uint8_t((g << 2) | (g >> 4)),
            uint8_t((b << 3) | (b >> 2)), 255};
    }

    Image decode_texture(size_t index) const {
        const Texture& tex = textures_.at(index);
        const size_t base = tex.offset;
        const uint32_t magic = u32(base);
        if (magic == 0x20504d42u) {
            const uint64_t pixels = uint64_t(tex.width) * tex.height;
            if (4 + pixels > tex.size) throw FormatError("Truncated ANIM BMP mask");
            Image image = allocate(tex.width, tex.height);
            for (size_t i = 0; i < size_t(pixels); ++i) {
                image.rgba[i * 4] = image.rgba[i * 4 + 1] = image.rgba[i * 4 + 2] = data_[base + 4 + i];
                image.rgba[i * 4 + 3] = 255;
            }
            return image;
        }
        if (magic != 0x20534444u) throw FormatError("Unsupported ANIM texture encoding");
        if (tex.size < 128) throw FormatError("Truncated DDS header");
        if (u32(base + 4) != 124 || u32(base + 76) != 32)
            throw FormatError("Invalid DDS header sizes");
        const uint32_t width = u32(base + 16), height = u32(base + 12);
        dimensions(width, height);
        if (width != tex.width || height != tex.height)
            throw FormatError("DDS dimensions disagree with ANIM descriptor");
        if (u32(base + 24) > 1 || u32(base + 112) != 0)
            throw FormatError("DDS arrays, cubemaps, and volume textures are unsupported");
        if (!(u32(base + 80) & 4)) throw FormatError("Only BC1/DXT1 and BC3/DXT5 DDS are supported");
        const uint32_t fourcc = u32(base + 84);
        const bool bc3 = fourcc == 0x35545844u;
        if (!bc3 && fourcc != 0x31545844u)
            throw FormatError("Unsupported DDS FourCC; expected DXT1 or DXT5");
        const uint32_t block_bytes = bc3 ? 16 : 8;
        const uint32_t mips = std::max(1u, u32(base + 28));
        uint32_t max_mips = 1;
        for (uint32_t dim = std::max(width, height); dim > 1; dim >>= 1) ++max_mips;
        if (mips > max_mips) throw FormatError("Invalid DDS mip count");
        uint64_t needed = 128;
        uint32_t mw = width, mh = height;
        for (uint32_t i = 0; i < mips; ++i) {
            needed += uint64_t((mw + 3) / 4) * ((mh + 3) / 4) * block_bytes;
            mw = std::max(1u, mw / 2); mh = std::max(1u, mh / 2);
        }
        if (needed > tex.size) throw FormatError("Truncated DDS block or mip data");
        Image image = allocate(width, height);
        size_t block = base + 128;
        for (uint32_t by = 0; by < (height + 3) / 4; ++by) {
            for (uint32_t bx = 0; bx < (width + 3) / 4; ++bx, block += block_bytes) {
                std::array<uint8_t, 8> alpha{};
                uint64_t alpha_bits = 0;
                if (bc3) {
                    alpha[0] = data_[block]; alpha[1] = data_[block + 1];
                    if (alpha[0] > alpha[1]) {
                        for (unsigned j = 1; j <= 6; ++j)
                            alpha[j + 1] = uint8_t(((7 - j) * alpha[0] + j * alpha[1]) / 7);
                    } else {
                        for (unsigned j = 1; j <= 4; ++j)
                            alpha[j + 1] = uint8_t(((5 - j) * alpha[0] + j * alpha[1]) / 5);
                        alpha[6] = 0; alpha[7] = 255;
                    }
                    for (unsigned j = 0; j < 6; ++j) alpha_bits |= uint64_t(data_[block + 2 + j]) << (8 * j);
                }
                const size_t color_at = block + (bc3 ? 8 : 0);
                const uint16_t c0 = u16(color_at), c1 = u16(color_at + 2);
                std::array<std::array<uint8_t, 4>, 4> colors{};
                colors[0] = color565(c0); colors[1] = color565(c1);
                if (bc3 || c0 > c1) {
                    for (unsigned c = 0; c < 3; ++c) {
                        colors[2][c] = uint8_t((2 * unsigned(colors[0][c]) + colors[1][c]) / 3);
                        colors[3][c] = uint8_t((unsigned(colors[0][c]) + 2 * colors[1][c]) / 3);
                    }
                    colors[2][3] = colors[3][3] = 255;
                } else {
                    for (unsigned c = 0; c < 3; ++c)
                        colors[2][c] = uint8_t((unsigned(colors[0][c]) + colors[1][c]) / 2);
                    colors[2][3] = 255; // BC1 entry 3 remains transparent black.
                }
                uint32_t color_bits = u32(color_at + 4);
                for (unsigned p = 0; p < 16; ++p) {
                    const auto& pixel = colors[(color_bits >> (2 * p)) & 3];
                    const uint32_t x = bx * 4 + p % 4, y = by * 4 + p / 4;
                    if (x >= width || y >= height) continue;
                    const size_t dst = (size_t(y) * width + x) * 4;
                    for (unsigned c = 0; c < 4; ++c) image.rgba[dst + c] = pixel[c];
                    if (bc3) image.rgba[dst + 3] = alpha[(alpha_bits >> (3 * p)) & 7];
                }
            }
        }
        return image;
    }
};

}} // namespace stardroid::remastered
#endif
