// Development-only comparison of caller-supplied classic and HD2 sprite geometry.
// Extracted artwork is written only when explicitly requested, outside the source tree.
#include "data_loading.h"
#include "../native/remastered/geometry.h"
#include <filesystem>
#include <fstream>
#include <iostream>
#include <set>

namespace fs = std::filesystem;
namespace scr = stardroid::remastered;
using Bytes = std::vector<uint8_t>;

static uint16_t u16(const Bytes& b, size_t p) { return unsigned(b.at(p)) | unsigned(b.at(p + 1)) << 8; }
static uint32_t u32(const Bytes& b, size_t p) { return uint32_t(u16(b, p)) | uint32_t(u16(b, p + 2)) << 16; }
static Bytes read(const fs::path& p) {
    if (!fs::is_regular_file(p) || fs::file_size(p) > 128u * 1024u * 1024u)
        throw std::runtime_error("Invalid or oversized animation file");
    std::ifstream f(p, std::ios::binary);
    Bytes b((std::istreambuf_iterator<char>(f)), {});
    if (f.bad()) throw std::runtime_error("Cannot read animation file");
    return b;
}
static std::string quoted(const std::string& s) {
    std::string out = "\"";
    for (unsigned char c : s) {
        if (c < 32 || c >= 127) throw std::runtime_error("Non-ASCII asset path");
        if (c == '\\' || c == '"') out += '\\';
        out += char(c);
    }
    return out + '"';
}
static void pam(const fs::path& p, const scr::Image& im) {
    if (!im.width || !im.height) return;
    std::ofstream f(p, std::ios::binary);
    f << "P7\nWIDTH " << im.width << "\nHEIGHT " << im.height
      << "\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n";
    f.write(reinterpret_cast<const char*>(im.rgba.data()), im.rgba.size());
    f.close();
    if (!f) throw std::runtime_error("Cannot write preview");
}
// Parse the original GRP RLE independently of the HD decoder; white is opaque.
static scr::Image grp_frame(const Bytes& b, size_t frame) {
    const size_t header = 6 + frame * 8;
    scr::Image im{b.at(header + 2), b.at(header + 3), {}};
    im.rgba.resize(size_t(im.width) * im.height * 4);
    const size_t start = u32(b, header + 4);
    for (size_t y = 0; y < im.height; ++y) {
        size_t pos = start + u16(b, start + y * 2), x = 0;
        while (x < im.width) {
            const auto code = b.at(pos++);
            const size_t length = code & ((code & 0x80) ? 0x7f : 0x3f);
            if (!length || length > im.width - x) throw std::runtime_error("Invalid original GRP run");
            if (code & 0x80) { x += length; continue; }
            if (code & 0x40) ++pos;
            else pos += length;
            if (pos > b.size()) throw std::runtime_error("Truncated original GRP pixels");
            for (size_t i = 0; i < length; ++i, ++x) {
                const size_t at = (y * im.width + x) * 4;
                for (size_t c = 0; c < 4; ++c) im.rgba[at + c] = 255;
            }
        }
    }
    return im;
}

int main(int argc, char** argv) {
    try {
        if (argc != 6 && argc != 7) throw std::runtime_error(
            "Usage: remastered-geometry STARDAT BROODAT PATCH HD2_ANIM_DIRECTORY REPORT.json [PRIVATE_PREVIEW_DIRECTORY]");
        bwgame::data_loading::data_files_loader<> loader;
        loader.add_mpq_file(argv[3]); loader.add_mpq_file(argv[2]); loader.add_mpq_file(argv[1]);
        auto load = [&](const std::string& name) {
            bwgame::a_vector<uint8_t> b; loader(b, name.c_str()); return Bytes(b.begin(), b.end());
        };
        const Bytes dat = load("arr/images.dat"), tbl = load("arr/images.tbl");
        std::vector<fs::path> files;
        for (const auto& entry : fs::directory_iterator(argv[4]))
            if (entry.is_regular_file() && entry.path().extension() == ".anim") files.push_back(entry.path());
        std::sort(files.begin(), files.end());
        if (files.empty()) throw std::runtime_error("No animation files");
        auto same_file = [](const fs::path& a, const fs::path& b) {
            return fs::weakly_canonical(a) == fs::weakly_canonical(b) ||
                (fs::exists(a) && fs::exists(b) && fs::equivalent(a, b));
        };
        for (int i = 1; i <= 3; ++i)
            if (same_file(argv[5], argv[i])) throw std::runtime_error("Report must not overwrite an input archive");
        for (const auto& p : files)
            if (same_file(argv[5], p)) throw std::runtime_error("Report must not overwrite an input animation");
        if (argc == 7) fs::create_directories(argv[6]);
        std::ofstream report(argv[5]);
        if (!report) throw std::runtime_error("Cannot create geometry report");
        report << "{\n\"scope\":\"Original MPQ geometry and supplied HD2 frame comparison; not a retail screenshot oracle\",\n\"assets\":[";
        size_t asset_count = 0, frame_count = 0, matching_count = 0;
        for (const auto& p : files) {
            const auto name = p.stem().string();
            if (name.size() != 8 || name.substr(0,5) != "main_" ||
                name.find_first_not_of("0123456789", 5) != std::string::npos)
                throw std::runtime_error("Expected original main_NNN.anim filenames");
            const unsigned id = std::stoul(name.substr(5));
            if (id >= 999) throw std::runtime_error("Image ID outside classic images.dat");
            const size_t index = u32(dat, id * 4);
            if (!index || index > u16(tbl,0)) throw std::runtime_error("Missing classic images.tbl reference");
            size_t off = u16(tbl,index * 2);
            std::string path = "unit/";
            while (tbl.at(off)) path += char(tbl.at(off++));
            const Bytes grp = load(path);
            const auto anim = scr::Anim::parse(read(p));
            if (anim.scale() != 2) throw std::runtime_error("Expected HD2 animation");
            if (asset_count++) report << ',';
            const unsigned classic_count = u16(grp,0), width = u16(grp,2), height = u16(grp,4);
            const bool frames_match = classic_count == anim.frames().size();
            matching_count += frames_match;
            report << "\n{\"file\":" << quoted(p.filename().string()) << ",\"image_id\":" << id
                   << ",\"classic_path\":" << quoted(path) << ",\"classic_canvas\":[" << width << ',' << height
                   << "],\"anim_canvas_hd\":[" << anim.canvas_width_hd() << ',' << anim.canvas_height_hd()
                   << "],\"classic_frame_count\":" << classic_count << ",\"hd2_frame_count\":" << anim.frames().size()
                   << ",\"frame_count_match\":" << (frames_match ? "true" : "false") << ",\"frames\":[";
            for (size_t f = 0; f < anim.frames().size(); ++f) {
                if (f) report << ',';
                const auto r = anim.frame_bounds(f);
                const auto placed = scr::place_frame(anim, f, 0, 0, false);
                const auto mirrored = scr::place_frame(anim, f, 0, 0, true);
                report << "{\"index\":" << f << ",\"hd2_offset\":[" << r.x_off << ',' << r.y_off
                       << "],\"hd2_size\":[" << r.width << ',' << r.height << ']'
                       << ",\"preview_relative_position\":[" << placed.left << ',' << placed.top << ']'
                       << ",\"preview_mirrored_relative_position\":[" << mirrored.left << ',' << mirrored.top << ']';
                if (f < classic_count) {
                    const size_t at = 6 + f * 8;
                    report << ",\"classic_offset\":[" << unsigned(grp.at(at)) << ',' << unsigned(grp.at(at+1))
                           << "],\"classic_size\":[" << unsigned(grp.at(at+2)) << ',' << unsigned(grp.at(at+3)) << ']';
                }
                report << '}';
                if (argc == 7 && (f == 0 || f == 8 || f == 16)) {
                    const auto prefix = fs::path(argv[6]) / (name + "_" + std::to_string(f));
                    pam(prefix.string() + "_hd2.pam", anim.frame_image(f, std::array<uint8_t,3>{244,4,4}));
                    if (f < classic_count) pam(prefix.string() + "_classic.pam", grp_frame(grp,f));
                }
                ++frame_count;
            }
            report << "]}";
        }
        report << "\n],\"asset_count\":" << asset_count << ",\"frame_count\":" << frame_count
               << ",\"matching_frame_counts\":" << matching_count << "}\n";
        report.close();
        if (!report) throw std::runtime_error("Cannot complete report");
        std::cout << "Compared " << asset_count << " HD2 files, " << frame_count
                  << " frames; " << matching_count << " classic frame counts match\n";
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
