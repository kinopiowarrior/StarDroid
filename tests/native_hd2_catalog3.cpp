// Check catalog identity against independently loaded classic DAT/TBL/GRP data,
// then exercise atomic replacement and the expanded renderer cache budget.
#include "data_loading.h"
#include "../native/remastered/runtime.h"
#include <iostream>

namespace hd = stardroid::remastered::live;
using Bytes = bwgame::a_vector<uint8_t>;

static unsigned u16(const Bytes& b, size_t at) {
    return b.at(at) | unsigned(b.at(at + 1)) << 8;
}
static void require(bool ok, const char* why) {
    if (!ok) throw std::runtime_error(why);
}
int main(int argc, char** argv) {
    try {
        require(argc == 8, "Usage: catalog STARDAT BROODAT PATCH FULL_DIR OLD_DIR INVALID_DIR OVER_BUDGET_DIR");
        bwgame::data_loading::data_files_loader<> storage;
        storage.add_mpq_file(argv[3]);
        storage.add_mpq_file(argv[2]);
        storage.add_mpq_file(argv[1]);
        auto load = [&](const std::string& name) {
            Bytes out;
            storage(out, name.c_str());
            return out;
        };
        const auto dat = bwgame::data_loading::load_images_dat(load("arr/images.dat"));
        const auto tbl = load("arr/images.tbl");
        constexpr unsigned normal = 0, shadow = 10, remap = 9;
        size_t shadow_count = 0;
        require(hd::configure(argv[4], true) == int(hd::body_count), "Full pack load failed");
        const auto full = std::atomic_load(&hd::active_pack);
        size_t accounted = 0;
        for (size_t slot = 0; slot < hd::body_count; ++slot) {
            const auto& entry = hd::bodies[slot];
            require(hd::slot_for(entry.id) == slot, "Image ID is ambiguous or missing from index");
            const auto& classic = dat.vec.at(entry.id);
            require(entry.modifier == unsigned(classic.modifier) && (entry.modifier == normal || entry.modifier == shadow),
                    "Catalog draw mode differs from images.dat");
            const int index = classic.grp_filename_index;
            require(index > 0 && unsigned(index) <= u16(tbl, 0), "Invalid GRP filename index");
            size_t off = u16(tbl, unsigned(index) * 2);
            std::string path = "unit/";
            while (tbl.at(off)) path += char(tbl.at(off++));
            const auto grp = load(path);
            require(entry.frames == u16(grp, 0) && entry.grp_width == u16(grp, 2)
                    && entry.grp_height == u16(grp, 4), "Catalog geometry differs from original GRP");
            const auto& atlas = full->atlases[slot];
            const auto& poses = full->poses[slot];
            require(!atlas.empty() && poses.size() == entry.frames, "Sprite missing real atlas or poses");
            accounted += atlas.size() * sizeof(hd::Texel) + poses.size() * sizeof(hd::Pose);
            if (entry.modifier == shadow) {
                bool opaque = false, fractional = false;
                for (const auto& p : atlas) {
                    opaque |= p.a == 255;
                    fractional |= p.a > 0 && p.a < 255;
                }
                require(opaque && fractional, "Shadow source has no soft silhouette");
                ++shadow_count;
            }
            std::cout << "IMAGE " << entry.id << ' ' << classic.modifier << ' ' << entry.frames
                      << ' ' << entry.grp_width << ' ' << entry.grp_height << ' ' << atlas.size() << '\n';
        }
        require(accounted == full->bytes && accounted <= hd::max_pack_bytes, "Invalid cache budget accounting");
        for (unsigned id : {361u, 508u}) {
            require(dat.vec.at(id).modifier == remap && hd::slot_for(id) == hd::body_count,
                    "Unsupported palette remap entered catalog");
        }
        require(hd::slot_for(999) == hd::body_count && hd::slot_for(~0u) == hd::body_count,
                "Out-of-range image ID should use classic graphics");
        require(hd::configure(argv[5], false) == 5 && std::atomic_load(&hd::active_pack) == full,
                "Validation changed active pack");
        require(hd::configure(argv[5], true) == 5, "Previous five-file pack rejected");
        const auto old = std::atomic_load(&hd::active_pack);
        for (unsigned id : {0u,5u,13u,122u,228u})
            require(!old->poses.at(hd::slot_for(id)).empty(), "Old import lost a body");
        require(old->poses.at(hd::slot_for(36)).empty(), "Partial pack unexpectedly includes Larva");
        require(hd::configure(argv[6], true) < 0 && old == std::atomic_load(&hd::active_pack),
                "Invalid replacement changed current pack");
        bool budget_error = false;
        try { hd::read_pack(argv[7]); }
        catch (const stardroid::remastered::FormatError& e) {
            budget_error = std::string(e.what()) == "HD2 sprite cache exceeds budget";
        }
        require(budget_error, "Oversized contribution did not reach cache limit");
        require(hd::configure(argv[7], true) < 0 && old == std::atomic_load(&hd::active_pack),
                "Over-budget replacement changed current pack");
        std::cout << "RESULT " << hd::body_count << ' ' << shadow_count << ' ' << accounted << ' '
                  << hd::max_pack_bytes << ' ' << old->bytes << '\n';
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
