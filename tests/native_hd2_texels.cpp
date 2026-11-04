// Compare every cached HD2 frame with the standalone decoder's unfiltered RGBA output.
#include "../native/remastered/runtime.h"
#include <fstream>
#include <iostream>
#include <iterator>

namespace scr = stardroid::remastered;
namespace hd = scr::live;
using Tint = std::array<uint8_t, 3>;

static void require(bool ok, const char* label) {
    if (!ok) throw std::runtime_error(label);
}

static size_t compare_draw(const hd::Pose& pose, const scr::Image& expected,
                           const scr::FramePlacement& rect, const Tint& tint,
                           int cx, int cy, bool flip, int w, int h) {
    std::vector<uint8_t> visited(size_t(expected.width) * expected.height);
    size_t draws = 0;
    hd::for_each_texel(pose, cx, cy, flip, tint.data(), w, h,
        [&](int x, int y, uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
            require(x >= 0 && x < w && y >= 0 && y < h, "Unclipped HD2 sample");
            const int fx = x - rect.left, fy = y - rect.top;
            require(fx >= 0 && fx < int(expected.width) && fy >= 0 && fy < int(expected.height),
                    "Sample outside reference placement");
            const int sx = flip ? int(expected.width) - 1 - fx : fx;
            const size_t i = size_t(fy) * expected.width + sx;
            const uint8_t* original = &expected.rgba[4 * i];
            require(!visited[i] && a && original[0] == r && original[1] == g && original[2] == b && original[3] == a,
                    "Duplicated or changed source texel");
            visited[i] = 1;
            ++draws;
        });
    for (int sy = 0; sy < int(expected.height); ++sy) {
        for (int sx = 0; sx < int(expected.width); ++sx) {
            const size_t i = size_t(sy) * expected.width + sx;
            const int x = rect.left + (flip ? int(expected.width) - 1 - sx : sx);
            const int y = rect.top + sy;
            const bool visible = x >= 0 && x < w && y >= 0 && y < h && expected.rgba[4 * i + 3];
            require(bool(visited[i]) == visible, "Missing or extra source texel");
        }
    }
    return draws;
}

int main(int argc, char** argv) {
    try {
        require(argc == 2, "Usage: native-hd2-texels HD2/anim");
        const std::string dir = argv[1];
        const auto pack = hd::read_pack(dir);
        require(pack->count == hd::body_count, "All five supported samples required");
        size_t frames = 0, pix = 0, draws = 0;
        for (size_t slot = 0; slot < hd::body_count; ++slot) {
            std::ifstream file(dir + '/' + hd::bodies[slot].filename, std::ios::binary);
            require(file.good(), "Missing source file");
            const auto anim = scr::Anim::parse({std::istreambuf_iterator<char>(file), {}});
            const auto oracle = anim.decode_sprite();
            const auto& diffuse = oracle.diffuse();
            const auto* mask = oracle.teamcolor();
            const auto& atlas = pack->atlases[slot];
            require(atlas.size() == size_t(diffuse.width) * diffuse.height, "Atlas resolution changed");
            for (uint32_t y = 0; y < diffuse.height; ++y) {
                for (uint32_t x = 0; x < diffuse.width; ++x) {
                    const size_t i = size_t(y) * diffuse.width + x;
                    const auto* src = &diffuse.rgba[i * 4];
                    const int team = mask && x < mask->width && y < mask->height ?
                        mask->rgba[(size_t(y) * mask->width + x) * 4] : 0;
                    const auto& t = atlas[i];
                    require(t.r == src[0] && t.g == src[1] && t.b == src[2] && t.a == src[3] && t.team == team,
                            "RGBA or team mask was quantized during load");
                }
            }
            pix += atlas.size();
            for (size_t i = 0; i < anim.frames().size(); ++i) {
                ++frames;
                const Tint tint{uint8_t(17 + i % 173), uint8_t(232 - i % 99), uint8_t(61 + i % 155)};
                const auto expected = oracle.frame_image(i, tint);
                const auto& pose = pack->poses[slot][i];
                for (bool flip : {false, true}) {
                    draws += compare_draw(pose, expected, scr::place_frame(anim, i, 128, 128, flip), tint,
                                          128, 128, flip, 512, 512);
                    if (i % 17 == 0) {
                        const int cx = i % 2 ? 2 : 28, cy = i % 3 ? -1 : 29;
                        draws += compare_draw(pose, expected, scr::place_frame(anim, i, cx, cy, flip), tint,
                                              cx, cy, flip, 55, 53);
                    }
                }
            }
        }
        require(pack->bytes == pix * sizeof(hd::Texel) + frames * sizeof(hd::Pose), "Incorrect cache accounting");
        require(pack->bytes <= hd::max_pack_bytes, "Unbounded resident cache");
        std::cout << "{\"frames\":" << frames << ",\"atlasTexels\":" << pix << ",\"drawnTexelsCompared\":" << draws
                  << ",\"cacheBytes\":" << pack->bytes << ",\"passed\":true}\n";
        return 0;
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
