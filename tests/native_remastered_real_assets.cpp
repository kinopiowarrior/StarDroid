// External-asset validation exporter. All game data/output remains caller-owned.
#include "../native/remastered/assets.h"
#include <filesystem>
#include <fstream>
#include <iostream>
namespace fs = std::filesystem;
namespace scr = stardroid::remastered;
static std::string quoted(const std::string& s) {
    std::string r = "\"";
    for (char c : s) { if (c == '"' || c == '\\') r += '\\'; r += c; }
    return r + '"';
}
static void bytes(std::ostream& out, const scr::Image& image) {
    out.write(reinterpret_cast<const char*>(image.rgba.data()), std::streamsize(image.rgba.size()));
    if (!out) throw std::runtime_error("Cannot write validation pixels");
}
int main(int argc, char** argv) {
    try {
        if (argc != 3) throw std::runtime_error("Usage: native-remastered-real-assets INPUT.anim OUTPUT_DIR");
        const fs::path input(argv[1]), output(argv[2]);
        const scr::Limits limits;
        const auto size = fs::file_size(input);
        if (size > limits.max_file_bytes) throw std::runtime_error("Input exceeds reader limit");
        std::vector<uint8_t> data(static_cast<size_t>(size));
        std::ifstream in(input, std::ios::binary);
        if (!in || !in.read(reinterpret_cast<char*>(data.data()), std::streamsize(size)))
            throw std::runtime_error("Cannot read sample");
        const auto anim = scr::Anim::parse(std::move(data));
        fs::create_directories(output);
        std::cout << "{\"scale\":" << unsigned(anim.scale()) << ",\"canvas_hd\":["
            << anim.canvas_width_hd() << ',' << anim.canvas_height_hd() << "],\"layers\":[";
        bool first = true;
        for (size_t i = 0; i < anim.layer_names().size(); ++i) {
            if (!anim.textures()[i].present()) continue;
            const auto layer = anim.decode_layer(anim.layer_names()[i]);
            std::ofstream file(output / ("layer-" + std::to_string(i) + ".rgba"), std::ios::binary);
            bytes(file, layer);
            if (!first) std::cout << ',';
            first = false;
            std::cout << "{\"index\":" << i << ",\"name\":" << quoted(anim.layer_names()[i])
                << ",\"width\":" << layer.width << ",\"height\":" << layer.height << '}';
        }
        const auto sprite = anim.decode_sprite();
        std::ofstream plain(output / "frames.rgba", std::ios::binary);
        std::ofstream red(output / "frames-red.rgba", std::ios::binary);
        std::ofstream blue(output / "frames-blue.rgba", std::ios::binary);
        std::cout << "],\"resident_pixel_bytes\":" << sprite.resident_bytes() << ",\"frames\":[";
        for (size_t i = 0; i < sprite.frames().size(); ++i) {
            const auto r = sprite.frame_bounds(i);
            const auto frame = sprite.frame_image(i);
            bytes(plain, frame);
            bytes(red, sprite.frame_image(i, std::array<uint8_t, 3>{255, 0, 0}));
            bytes(blue, sprite.frame_image(i, std::array<uint8_t, 3>{0, 0, 255}));
            if (i) std::cout << ',';
            std::cout << '[' << r.tex_x << ',' << r.tex_y << ',' << r.width << ',' << r.height << ','
                << r.x_off << ',' << r.y_off << ']';
        }
        std::cout << "]}\n";
        return 0;
    } catch (const std::exception& e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
