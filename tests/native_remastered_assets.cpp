#include "../native/remastered/assets.h"
#include <fstream>
#include <functional>
#include <iostream>
#include <string>

using namespace stardroid::remastered;
using Bytes = std::vector<uint8_t>;

static unsigned checks = 0;
static void check(bool ok, const char* name) {
    ++checks;
    if (!ok) throw std::runtime_error(std::string("Failed: ") + name);
}
static void rejects(const std::function<void()>& action, const char* name) {
    bool rejected = false;
    try { action(); } catch (const FormatError&) { rejected = true; }
    check(rejected, name);
}
static void put16(Bytes& b, size_t at, uint16_t value) {
    b.at(at) = uint8_t(value); b.at(at + 1) = uint8_t(value >> 8);
}
static void put32(Bytes& b, size_t at, uint32_t value) {
    for (unsigned j = 0; j < 4; ++j) b.at(at + j) = uint8_t(value >> (j * 8));
}
static uint32_t get32(const Bytes& b, size_t at) {
    return uint32_t(b.at(at)) | (uint32_t(b.at(at + 1)) << 8) |
        (uint32_t(b.at(at + 2)) << 16) | (uint32_t(b.at(at + 3)) << 24);
}
static Bytes dds(bool bc3, uint16_t width, uint16_t height, uint16_t c0,
    uint16_t c1, uint32_t selectors, uint8_t a0 = 255, uint8_t a1 = 0,
    uint64_t alpha_selectors = 0) {
    Bytes b(128 + (bc3 ? 16 : 8));
    put32(b, 0, 0x20534444); put32(b, 4, 124); put32(b, 8, 0x81007);
    put32(b, 12, height); put32(b, 16, width); put32(b, 20, bc3 ? 16 : 8);
    put32(b, 76, 32); put32(b, 80, 4); put32(b, 84, bc3 ? 0x35545844 : 0x31545844);
    put32(b, 108, 0x1000);
    size_t colors = 128;
    if (bc3) {
        b[128] = a0; b[129] = a1;
        for (unsigned i = 0; i < 6; ++i) b[130 + i] = uint8_t(alpha_selectors >> (8 * i));
        colors += 8;
    }
    put16(b, colors, c0); put16(b, colors + 2, c1); put32(b, colors + 4, selectors);
    return b;
}

// All bytes are original synthetic fixture data. No Blizzard resources needed.
static Bytes anim(const Bytes& diffuse, uint8_t scale = 2, uint16_t width = 4,
    uint16_t height = 4, const Bytes& mask = {}) {
    const uint16_t layers = mask.empty() ? 1 : 2;
    const size_t frame_at = 0x158 + size_t(layers) * 12;
    const size_t diffuse_at = frame_at + 32;
    const size_t mask_at = diffuse_at + diffuse.size();
    Bytes b(mask_at + mask.size());
    put32(b, 0, 0x4d494e41); b[4] = scale; b[5] = 2;
    put16(b, 8, layers); put16(b, 10, 1);
    const std::string names[] = {"diffuse", "teamcolor"};
    for (unsigned n = 0; n < layers; ++n)
        std::copy(names[n].begin(), names[n].end(), b.begin() + 12 + n * 32);
    put16(b, 0x14c, 2); put16(b, 0x14e, 0xffff);
    put16(b, 0x150, 24); put16(b, 0x152, 32); put32(b, 0x154, uint32_t(frame_at));
    put32(b, 0x158, uint32_t(diffuse_at)); put32(b, 0x15c, uint32_t(diffuse.size()));
    put16(b, 0x160, width); put16(b, 0x162, height);
    if (layers == 2) {
        put32(b, 0x164, uint32_t(mask_at)); put32(b, 0x168, uint32_t(mask.size()));
        put16(b, 0x16c, width); put16(b, 0x16e, height);
    }
    put16(b, frame_at + 4, uint16_t(-3)); put16(b, frame_at + 6, uint16_t(-1));
    const uint16_t multiplier = scale == 2 ? 2 : 1;
    put16(b, frame_at + 8, width * multiplier); put16(b, frame_at + 10, height * multiplier);
    put32(b, frame_at + 12, 9);
    // The second frame has zero extent, representing a legal blank frame.
    std::copy(diffuse.begin(), diffuse.end(), b.begin() + diffuse_at);
    std::copy(mask.begin(), mask.end(), b.begin() + mask_at);
    return b;
}
static std::array<uint8_t, 4> pixel(const Image& im, size_t x, size_t y = 0) {
    const size_t at = (y * im.width + x) * 4;
    return {im.rgba.at(at), im.rgba.at(at + 1), im.rgba.at(at + 2), im.rgba.at(at + 3)};
}
static Bytes fixture() {
    Bytes mask(20, 0); put32(mask, 0, 0x20504d42);
    for (unsigned i = 8; i < 16; ++i) mask[4 + i] = 255;
    return anim(dds(true, 4, 4, 0xffff, 0, 0), 2, 4, 4, mask);
}

static void run_tests() {
    const Bytes bc1 = anim(dds(false, 4, 4, 0xf800, 0x001f, 0xe4e4e4e4));
    const Anim basic = Anim::parse(bc1);
    const Image im = basic.decode_layer("diffuse");
    check(im.width == 4 && im.height == 4 && im.rgba.size() == 64, "BC1 dimensions");
    check(pixel(im, 0) == std::array<uint8_t, 4>{255, 0, 0, 255}, "BC1 red endpoint");
    check(pixel(im, 1) == std::array<uint8_t, 4>{0, 0, 255, 255}, "BC1 blue endpoint");
    check(pixel(im, 2) == std::array<uint8_t, 4>{170, 0, 85, 255}, "BC1 2/3 interpolation");
    check(pixel(im, 3) == std::array<uint8_t, 4>{85, 0, 170, 255}, "BC1 1/3 interpolation");
    const Image bc1_alpha = Anim::parse(anim(dds(false, 4, 4, 0, 0xffff, 0xe4e4e4e4))).decode_layer("diffuse");
    check(pixel(bc1_alpha, 2) == std::array<uint8_t, 4>{127, 127, 127, 255}, "BC1 three-color average");
    check(pixel(bc1_alpha, 3) == std::array<uint8_t, 4>{0, 0, 0, 0}, "BC1 transparent selector");

    uint64_t alpha_indices = 0;
    for (unsigned p = 0; p < 16; ++p) alpha_indices |= uint64_t(p % 8) << (p * 3);
    const Image alpha7 = Anim::parse(anim(dds(true, 4, 4, 0, 0xffff, 0xeeeeeeee,
        70, 0, alpha_indices))).decode_layer("diffuse");
    const uint8_t expected7[] = {70, 0, 60, 50, 40, 30, 20, 10};
    for (unsigned p = 0; p < 16; ++p)
        check(alpha7.rgba[p * 4 + 3] == expected7[p % 8], "BC3 seven-step alpha palette");
    check(pixel(alpha7, 0)[0] == 85 && pixel(alpha7, 1)[0] == 170,
        "BC3 always uses four colors regardless of endpoint order");
    const Image alpha5 = Anim::parse(anim(dds(true, 4, 4, 0xffff, 0, 0,
        0, 50, alpha_indices))).decode_layer("diffuse");
    const uint8_t expected5[] = {0, 50, 10, 20, 30, 40, 0, 255};
    for (unsigned p = 0; p < 16; ++p)
        check(alpha5.rgba[p * 4 + 3] == expected5[p % 8], "BC3 five-step alpha palette");
    check(pixel(alpha5, 0) == std::array<uint8_t, 4>{255, 255, 255, 0}, "RGBA remains straight alpha");

    const Image odd = Anim::parse(anim(dds(false, 3, 2, 0xf800, 0x001f, 0xe4e4e4e4), 2, 3, 2)).frame_image(0);
    check(odd.width == 3 && odd.height == 2 && odd.rgba.size() == 24, "Partial BC block crop");
    check(pixel(odd, 2, 1) == std::array<uint8_t, 4>{170, 0, 85, 255}, "Partial block row stride");

    // A 12x8 atlas has six independently colored blocks. The 6x4 frame at (3,3)
    // crosses both block rows and all three columns, exposing atlas/crop strides.
    Bytes atlas = dds(false, 12, 8, 0, 0, 0);
    atlas.resize(128 + 6 * 8); put32(atlas, 20, 6 * 8);
    const uint16_t block_colors[] = {0xf800, 0x07e0, 0x001f, 0xffff, 0xffe0, 0xf81f};
    for (size_t i = 0; i < 6; ++i) put16(atlas, 128 + i * 8, block_colors[i]);
    Bytes cropped = anim(atlas, 2, 12, 8);
    const size_t cropped_at = get32(cropped, 0x154);
    put16(cropped, cropped_at, 6); put16(cropped, cropped_at + 2, 6);
    put16(cropped, cropped_at + 8, 12); put16(cropped, cropped_at + 10, 8);
    const Anim atlas_anim = Anim::parse(cropped);
    const Image atlas_frame = atlas_anim.frame_image(0);
    const FrameRegion atlas_region = atlas_anim.frame_bounds(0);
    check(atlas_region.tex_x == 3 && atlas_region.tex_y == 3 &&
        atlas_frame.width == 6 && atlas_frame.height == 4, "HD2 nonzero atlas crop dimensions");
    const std::array<uint8_t, 4> colors[] = {{255, 0, 0, 255}, {0, 255, 0, 255},
        {0, 0, 255, 255}, {255, 255, 255, 255}, {255, 255, 0, 255}, {255, 0, 255, 255}};
    const unsigned expected_blocks[4][6] = {{0, 1, 1, 1, 1, 2},
        {3, 4, 4, 4, 4, 5}, {3, 4, 4, 4, 4, 5}, {3, 4, 4, 4, 4, 5}};
    for (size_t y = 0; y < 4; ++y)
        for (size_t x = 0; x < 6; ++x)
            check(pixel(atlas_frame, x, y) == colors[expected_blocks[y][x]],
                "Non-square atlas crop crosses distinct block colors");
    check(basic.scale() == 2 && basic.canvas_width_hd() == 24 && basic.canvas_height_hd() == 32, "Header metadata");
    check(basic.frames()[0].flags == 9 && basic.frames()[0].x_off == -3, "Raw frame metadata retained");
    const FrameRegion hd2 = basic.frame_bounds(0);
    check(hd2.width == 4 && hd2.height == 4 && hd2.x_off == -2 && hd2.y_off == -1, "HD2 signed floor rounding");
    const FrameRegion hd = Anim::parse(anim(dds(false, 4, 4, 0xffff, 0, 0), 4)).frame_bounds(0);
    check(hd.width == 4 && hd.x_off == -3 && hd.y_off == -1, "HD raw-coordinate preservation");
    const Image blank = basic.frame_image(1);
    check(blank.width == 0 && blank.height == 0 && blank.rgba.empty(), "Blank frame yields no pixels");
    for (unsigned zero_axis = 0; zero_axis < 2; ++zero_axis) {
        Bytes edge = bc1;
        const size_t frame_at = get32(edge, 0x154);
        put16(edge, frame_at, 65535); put16(edge, frame_at + 2, 65535);
        put16(edge, frame_at + 8 + zero_axis * 2, 0);
        check(Anim::parse(edge).frame_image(0).rgba.empty(), "Zero extent ignores blank crop coordinates");
    }
    Bytes negative = bc1; put16(negative, get32(negative, 0x154) + 4, 0x8000);
    check(Anim::parse(negative).frame_bounds(0).x_off == -16384, "Maximum negative HD2 offset");
    rejects([&]{ basic.frame_image(2); }, "Frame index bounds");
    rejects([&]{ basic.decode_layer("unknown"); }, "Missing layer explicit");

    const Anim colored = Anim::parse(fixture());
    const Image colored_frame = colored.frame_image(0, std::array<uint8_t, 3>{255, 0, 0});
    check(pixel(colored_frame, 0, 0) == std::array<uint8_t, 4>{255, 255, 255, 255}, "Unmasked diffuse unchanged");
    check(pixel(colored_frame, 0, 3) == std::array<uint8_t, 4>{255, 0, 0, 255}, "Masked white becomes team color");
    const Image mask = colored.decode_layer("teamcolor");
    check(pixel(mask, 0, 0) == std::array<uint8_t, 4>{0, 0, 0, 255}, "BMP mask black export");
    check(pixel(mask, 0, 3) == std::array<uint8_t, 4>{255, 255, 255, 255}, "BMP mask white export");
    Bytes partial_mask(20, 128); put32(partial_mask, 0, 0x20504d42);
    const Image partial_tint = Anim::parse(anim(dds(true, 4, 4, 0x8410, 0, 0, 70),
        2, 4, 4, partial_mask)).frame_image(0, std::array<uint8_t, 3>{0, 128, 255});
    check(pixel(partial_tint, 0, 0) == std::array<uint8_t, 4>{66, 98, 132, 70},
        "Partial mask modulates colored diffuse and preserves original alpha");

    // Every truncation point must fail without an out-of-bounds read.
    for (size_t size = 0; size < bc1.size(); ++size)
        rejects([&]{ Anim::parse(Bytes(bc1.begin(), bc1.begin() + size)); }, "Truncated ANIM rejected");
    auto mutate_parse = [&](size_t at, uint32_t value, const char* name) {
        Bytes b = bc1; put32(b, at, value); rejects([&]{ Anim::parse(b); }, name);
    };
    mutate_parse(0, 0, "ANIM magic");
    mutate_parse(0x154, 0xfffffff0, "Frame pointer overflow");
    mutate_parse(0x158, 0xfffffff0, "Texture pointer overflow");
    mutate_parse(0x15c, 0xffffffff, "Texture size overflow");
    mutate_parse(0x154, 0, "Frame metadata overlap");
    mutate_parse(0x158, get32(bc1, 0x154), "Frame payload overlap");
    Bytes b = bc1; b[5] = 1; rejects([&]{ Anim::parse(b); }, "SD variant explicit rejection");
    b = bc1; b[4] = 8; rejects([&]{ Anim::parse(b); }, "Unknown scale rejection");
    b = bc1; put16(b, 8, 11); rejects([&]{ Anim::parse(b); }, "Layer count upper bound");
    b = bc1; put16(b, 0x14c, 0); rejects([&]{ Anim::parse(b); }, "Zero frame count rejection");
    b = bc1; put16(b, 0x14e, 0); rejects([&]{ Anim::parse(b); }, "Reference explicit rejection");
    b = bc1; put16(b, get32(b, 0x154), 8); rejects([&]{ Anim::parse(b); }, "Frame crop outside atlas");
    b = fixture(); std::copy(b.begin() + 12, b.begin() + 44, b.begin() + 44);
    rejects([&]{ Anim::parse(b); }, "Duplicate layer rejection");
    b = fixture(); put32(b, 0x164, get32(b, 0x158));
    rejects([&]{ Anim::parse(b); }, "Overlapping layer payload rejection");
    b = bc1; put32(b, 0x158, 0); rejects([&]{ Anim::parse(b); }, "Absent texture inconsistent length");

    const size_t texture_at = get32(bc1, 0x158);
    auto mutate_dds = [&](size_t relative, uint32_t value, const char* name) {
        Bytes d = bc1; put32(d, texture_at + relative, value);
        rejects([&]{ Anim::parse(d).decode_layer("diffuse"); }, name);
    };
    mutate_dds(0, 0, "Unknown texture encoding");
    mutate_dds(4, 123, "DDS header length");
    mutate_dds(76, 31, "DDS pixel format header length");
    mutate_dds(16, 8, "DDS versus ANIM dimensions");
    mutate_dds(112, 0x200, "DDS cubemap explicit rejection");
    mutate_dds(24, 2, "DDS depth explicit rejection");
    mutate_dds(80, 0, "Non-FourCC DDS rejection");
    mutate_dds(84, 0x30315844, "DX10 explicit rejection");
    mutate_dds(28, 100, "DDS excessive mip count");
    mutate_dds(28, 2, "Missing declared mip data");
    Bytes complete_mips = dds(false, 4, 4, 0xf800, 0x001f, 0xe4e4e4e4);
    put32(complete_mips, 28, 2); complete_mips.resize(complete_mips.size() + 8);
    check(Anim::parse(anim(complete_mips)).decode_layer("diffuse").rgba == im.rgba,
        "Complete second mip accepted; preview decodes base level");
    complete_mips.pop_back();
    rejects([&]{ Anim::parse(anim(complete_mips)).decode_layer("diffuse"); }, "One-byte-short final mip");
    b = bc1; b.pop_back(); put32(b, 0x15c, uint32_t(b.size() - texture_at));
    rejects([&]{ Anim::parse(b).decode_layer("diffuse"); }, "Incomplete BC1 block");
    b = fixture(); b.pop_back(); put32(b, 0x168, 19);
    rejects([&]{ Anim::parse(b).decode_layer("teamcolor"); }, "Truncated BMP mask");

    Limits limits; limits.max_file_bytes = bc1.size() - 1;
    rejects([&]{ Anim::parse(bc1, limits); }, "Input byte cap");
    limits = {}; limits.max_decoded_pixels = 15;
    rejects([&]{ Anim::parse(bc1, limits); }, "Decoded pixel cap");
    limits = {}; limits.max_frames = 1;
    rejects([&]{ Anim::parse(bc1, limits); }, "Frame count cap");
    limits = {}; limits.max_dimension = 3;
    rejects([&]{ Anim::parse(bc1, limits); }, "Dimension cap");
    limits = {}; limits.max_working_pixels = 47;
    rejects([&]{ Anim::parse(fixture(), limits).frame_image(0, std::array<uint8_t, 3>{1, 2, 3}); }, "Working pixel cap");
}

int main(int argc, char** argv) {
    try {
        if (argc == 3 && std::string(argv[1]) == "--write-fixture") {
            const Bytes data = fixture();
            std::ofstream out(argv[2], std::ios::binary);
            out.write(reinterpret_cast<const char*>(data.data()), std::streamsize(data.size()));
            if (!out) throw std::runtime_error("Cannot write synthetic fixture");
            return 0;
        }
        if (argc != 1) throw std::runtime_error("Usage: native_remastered_assets [--write-fixture PATH]");
        run_tests();
        std::cout << "PASS " << checks << " synthetic Remastered asset checks\n";
        return 0;
    } catch (const std::exception& e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
