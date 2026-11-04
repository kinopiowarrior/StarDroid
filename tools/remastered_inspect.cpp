// Development-only reader for caller-supplied, extracted Remastered sprites.
#include "../native/remastered/assets.h"

#include <array>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <limits>
#include <optional>
#include <string>

namespace fs = std::filesystem;
namespace scr = stardroid::remastered;

static std::string json_string(const std::string& text) {
    static const char hex[] = "0123456789abcdef";
    std::string out = "\"";
    for (unsigned char c : text) {
        if (c == '"' || c == '\\') { out += '\\'; out += char(c); }
        else if (c < 0x20) {
            out += "\\u00"; out += hex[c >> 4]; out += hex[c & 15];
        } else out += char(c);
    }
    return out + '"';
}

static size_t frame_number(const std::string& arg) {
    if (arg.empty()) throw std::runtime_error("Missing frame number");
    size_t value = 0;
    for (char c : arg) {
        if (c < '0' || c > '9' || value > (std::numeric_limits<size_t>::max() - size_t(c - '0')) / 10)
            throw std::runtime_error("Invalid frame number");
        value = value * 10 + size_t(c - '0');
    }
    return value;
}

static std::array<uint8_t, 3> color(const std::string& arg) {
    if (arg.size() != 6) throw std::runtime_error("Team color must be six hexadecimal digits, e.g. ff0000");
    std::array<uint8_t, 3> out{};
    for (size_t i = 0; i < 6; ++i) {
        const char c = arg[i];
        const int v = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
        if (v < 0) throw std::runtime_error("Invalid hexadecimal team color");
        out[i / 2] = uint8_t(out[i / 2] * 16 + v);
    }
    return out;
}

static std::vector<uint8_t> load_file(const fs::path& path, size_t limit) {
    if (!fs::is_regular_file(path)) throw std::runtime_error("Input must be an extracted regular .anim file");
    const auto size = fs::file_size(path);
    if (size > limit) throw std::runtime_error("Input exceeds the file-size limit");
    std::vector<uint8_t> data(static_cast<size_t>(size));
    std::ifstream file(path, std::ios::binary);
    if (!file || (size && !file.read(reinterpret_cast<char*>(data.data()), static_cast<std::streamsize>(size))))
        throw std::runtime_error("Cannot read complete input file");
    if (file.peek() != std::char_traits<char>::eof()) throw std::runtime_error("Input changed while reading");
    return data;
}

static void write_pam(const fs::path& path, const fs::path& input, const scr::Image& image) {
    if (image.width == 0 || image.height == 0)
        throw std::runtime_error("This frame is empty; there are no preview pixels to export");
    if (fs::weakly_canonical(path) == fs::weakly_canonical(input) ||
        (fs::exists(path) && fs::equivalent(path, input)))
        throw std::runtime_error("Preview must not overwrite the source asset");
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out) throw std::runtime_error("Cannot create preview file");
    out << "P7\nWIDTH " << image.width << "\nHEIGHT " << image.height
        << "\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n";
    out.write(reinterpret_cast<const char*>(image.rgba.data()), static_cast<std::streamsize>(image.rgba.size()));
    out.close();
    if (!out) throw std::runtime_error("Cannot write complete preview file");
}

int main(int argc, char** argv) {
    try {
        if (argc == 1 || (argc == 2 && std::string(argv[1]) == "--help")) {
            std::cout << "Usage: remastered-inspect EXTRACTED.anim [--frame N] [--team-color RRGGBB] [--preview FRAME.pam]\n"
                         "Reports validated HD2/HD frame metadata and decodes one diffuse frame.\n"
                         "PAM previews retain RGBA transparency. CASC input and in-game rendering are not implemented.\n";
            return argc == 1 ? 2 : 0;
        }
        const fs::path input(argv[1]);
        fs::path preview;
        size_t frame = 0;
        std::optional<std::array<uint8_t, 3>> team_color;
        for (int i = 2; i < argc; ++i) {
            const std::string option(argv[i]);
            if (i + 1 >= argc) throw std::runtime_error("Missing option value: " + option);
            const std::string value(argv[++i]);
            if (option == "--frame") frame = frame_number(value);
            else if (option == "--preview") preview = value;
            else if (option == "--team-color") team_color = color(value);
            else throw std::runtime_error("Unknown option: " + option);
        }
        const scr::Limits limits;
        const auto anim = scr::Anim::parse(load_file(input, limits.max_file_bytes), limits);
        const auto bounds = anim.frame_bounds(frame);
        const auto image = anim.frame_image(frame, team_color);
        if (!preview.empty()) write_pam(preview, input, image);
        std::cout << "{\n  \"format\": \"ANIM\",\n  \"scale\": " << unsigned(anim.scale())
                  << ",\n  \"frame_count\": " << anim.frames().size()
                  << ",\n  \"canvas_hd\": [" << anim.canvas_width_hd() << ',' << anim.canvas_height_hd() << ']'
                  << ",\n  \"layers\": [";
        for (size_t i = 0; i < anim.layer_names().size(); ++i) {
            if (i) std::cout << ',';
            const auto& texture = anim.textures()[i];
            std::cout << "{\"name\":" << json_string(anim.layer_names()[i])
                      << ",\"present\":" << (texture.present() ? "true" : "false")
                      << ",\"width\":" << texture.width << ",\"height\":" << texture.height << '}';
        }
        std::cout << "],\n  \"frame\": " << frame
                  << ",\n  \"texture_crop\": [" << bounds.tex_x << ',' << bounds.tex_y << ',' << bounds.width << ',' << bounds.height << ']'
                  << ",\n  \"offset_pixels\": [" << bounds.x_off << ',' << bounds.y_off << ']'
                  << ",\n  \"decoded_pixels\": " << uint64_t(image.width) * image.height
                  << ",\n  \"renderer_integrated\": false\n}\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "Remastered asset error: " << error.what() << '\n';
        return 2;
    }
}
