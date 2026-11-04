// Render the exact same authored Dragoon with the previous Android readback and
// with the current JNI frame output. Assets and pixel captures stay external.
#include "runtime.cpp"
#include <algorithm>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>

namespace fs = std::filesystem;
using namespace bwgame;
static constexpr unsigned width = 640, height = 480;
static void need(bool value, const char* message) { if (!value) throw std::runtime_error(message); }

static std::vector<uint8_t> world_bytes() {
    std::vector<uint8_t> bytes;
    native_save::Codec codec(false, bytes, g_ui->st);
    codec.world();
    return bytes;
}

struct Target {
    unsigned density;
    size_t pitch;
    std::vector<uint8_t> bytes;

    explicit Target(unsigned scale) : density(scale), pitch(4 * width * scale + 28),
        bytes(pitch * height * scale + 32, 0x6d) {}

    void render() {
        const auto camera = g_ui->screen_pos;
        const auto pointer_world = g_ui->screen_to_map(319, 181);
        const int tick = g_ui->event_tick;
        const auto frame = g_ui->st.current_frame;
        g_ui->external_minimap = true;
#ifdef HD2_TEST_PREVIEW1
        need(density == 1, "Preview 1 supports one pixel per map pixel");
        openbw_render(0);
        for (unsigned y = 0; y < height; ++y)
            std::memcpy(bytes.data() + y * pitch, openbw_framebuffer() + y * width * 4, width * 4);
#else
        render_world_pixels(bytes.data(), pitch, width * density, height * density, 0.0);
#endif
        need(openbw_framebuffer_width() == int(width) && openbw_framebuffer_height() == int(height),
             "Presentation density changed the logical viewport");
        need(g_ui->event_tick == tick + 1 && g_ui->st.current_frame == frame,
             "Presentation ran more than once or advanced the game");
        need(g_ui->screen_pos == camera && g_ui->screen_to_map(319, 181) == pointer_world,
             "Presentation moved the camera or logical pointer");
    }
    void check_padding() const {
        for (unsigned y = 0; y < height * density; ++y)
            for (size_t i = width * density * 4; i < pitch; ++i)
                need(bytes[y * pitch + i] == 0x6d, "Android row padding was overwritten");
        need(std::all_of(bytes.end() - 32, bytes.end(), [](uint8_t v) { return v == 0x6d; }),
             "Frame write exceeded the target");
    }
    void capture(const fs::path& path) const {
        std::ofstream file(path, std::ios::binary);
        file << "P6\n" << width * density << ' ' << height * density << "\n255\n";
        std::vector<uint8_t> row(width * density * 3);
        for (unsigned y = 0; y < height * density; ++y) {
            for (unsigned x = 0; x < width * density; ++x)
                std::memcpy(row.data() + x * 3, bytes.data() + y * pitch + x * 4, 3);
            file.write(reinterpret_cast<const char*>(row.data()), std::streamsize(row.size()));
        }
        need(file.good(), "Cannot write private capture");
    }
};

static void case_first_strike(const fs::path& pack, const fs::path& output) {
    need(set_hd2_sprites(pack.string(), true) == 5, "All five fixtures must load");
    openbw_init_campaign(width, height, 201);
    g_ui->fog_player = -1;
    unit_t* dragoon = nullptr;
    for (auto& group : g_ui->st.player_units) for (auto* unit : ptr(group)) {
        if (g_ui->unit_is(unit, UnitTypes::Protoss_Dragoon) && !g_ui->unit_dying(unit)) { dragoon = unit; break; }
    }
    need(dragoon, "First Strike Dragoon missing");
    g_ui->screen_pos = dragoon->position - xy(width / 2, 180);
    g_ui->clamp_viewport();
#ifdef HD2_TEST_PREVIEW1
    Target target(1);
#else
    Target target(2);
#endif
    g_ui->current_selection_clear();
    target.render(); // initialize the presentation surfaces
    const auto initial = world_bytes();
    target.render();
    target.capture(output / "body.ppm");
    auto* image = dragoon->sprite->main_image;
    const unsigned flags = image->flags;
    const auto center = g_ui->get_image_center_map_position(image) - g_ui->screen_pos;
    const unsigned owner = dragoon->owner;
    const auto tint = [&](unsigned player_color) {
        const auto ramp = g_ui->unit_player_colors101(owner, g_ui->display_player_color(player_color, owner));
        const auto* rgb = &g_ui->tileset_img.wpe[size_t(ramp[4])*4];
        return std::array<unsigned,3>{rgb[0], rgb[1], rgb[2]};
    };
    const unsigned player_color = g_ui->st.players[owner].color;
    const auto c = tint(player_color);
    const unsigned other_color = (player_color+1)%8;
    g_ui->st.players[owner].color = other_color;
    const auto c2 = tint(other_color);
    g_ui->st.players[owner].color = player_color;
    need(c != c2, "Alternate owner must change the palette");
    const auto sprite = dragoon->sprite;
    const auto circle_id = unsigned(ImageTypes::IMAGEID_Selection_Circle_22pixels) + sprite->sprite_type->selection_circle;
    const auto& circle = g_ui->eud109_current_grp109(circle_id)->frames.at(0);
    const int health_width = sprite->sprite_type->health_bar_size;
    const int gauge_width = std::max(19,health_width - (health_width-1)%3);
    const auto sprite_at = sprite->position - g_ui->screen_pos;
    const int gauge_left = sprite_at.x - gauge_width/2;
    const int gauge_top = sprite_at.y + sprite->sprite_type->selection_circle_vpos + circle.size.y/2 + 8 - 7/2;
    std::ofstream meta(output / "placement.json");
    meta << "{\"density\":" << target.density << ",\"frame\":" << image->frame_index
         << ",\"center\":[" << center.x << ',' << center.y << "],\"camera\":[" << g_ui->screen_pos.x << ',' << g_ui->screen_pos.y
         << "],\"gauge\":[" << gauge_left << ',' << gauge_top << ',' << gauge_width << ",7],\"flipped\":" << (flags & image_t::flag_horizontally_flipped ? "true" : "false")
         << ",\"tint\":[" << c[0] << ',' << c[1] << ',' << c[2] << "],\"otherTint\":[" << c2[0] << ',' << c2[1] << ',' << c2[2] << "]}\n";
    meta.close(); need(meta.good(), "Cannot write placement metadata");
    image->flags ^= image_t::flag_horizontally_flipped;
    target.render(); target.capture(output / "flipped.ppm");
    image->flags = flags;
    g_ui->st.players[owner].color = other_color;
    target.render(); target.capture(output / "owner.ppm");
    g_ui->st.players[owner].color = player_color;
    image->flags |= image_t::flag_hidden;
    target.render(); target.capture(output / "background.ppm");
    image->flags = flags;
    g_ui->current_selection_add(dragoon);
    target.render(); target.capture(output / "selected.ppm");
    // A hidden main image also skips its selection circle. Keep the ordinary
    // visible-image pass and its stock circle, but make just this body's GRP
    // frame empty so we can measure the exact background beneath its alpha.
    const auto* original_grp = image->grp;
    grp_t no_body{};
    no_body.width = original_grp->width;
    no_body.height = original_grp->height;
    no_body.frames.resize(image->frame_index + 1);
    no_body.frames.at(image->frame_index).size = {0,0};
    image->grp = &no_body;
    target.render(); target.capture(output / "selected-background.ppm");
    image->grp = original_grp;
    g_ui->current_selection_clear();
    g_ui->is_drag_selecting = true;
    g_ui->drag_select_from_x = center.x-34; g_ui->drag_select_from_y = center.y;
    g_ui->drag_select_to_x = center.x+34; g_ui->drag_select_to_y = center.y+36;
    target.render(); target.capture(output / "drag.ppm");
    g_ui->is_drag_selecting = false;
#ifndef HD2_TEST_PREVIEW1
    Target reduced(1); reduced.render(); reduced.capture(output / "reduced.ppm");
#endif
    target.render();
    target.check_padding();
    need(world_bytes() == initial, "Body rendering changed the synchronized game");
    std::vector<double> times;
    for (int i = 0; i < 17; ++i) {
        const auto begin = std::chrono::steady_clock::now();
        target.render();
        if (i >= 5) times.push_back(std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now() - begin).count());
    }
    std::sort(times.begin(), times.end());
    std::cout << "TIMING " << (times[5]+times[6])/2.0 << ' ' << times.back() << '\n';
    for (int i = 0; i < 32; ++i) { native_step(); if (i % 4 == 0) target.render(); }
    const auto continued = world_bytes();
    std::ofstream out(output / "continuation.bin", std::ios::binary);
    out.write(reinterpret_cast<const char*>(continued.data()),std::streamsize(continued.size()));
    need(out.good(),"Cannot write world comparison");
    std::cout << "PASS First Strike render, row-stride sentinels, single presentation tick and stable viewport\n";
    close_game();
}
int main(int argc, char** argv) {
    try {
        need(argc == 7, "Usage: test STARDAT BROODAT PATCH STARCRAFT ANIM_DIR PRIVATE_OUTPUT");
        for (int i = 0; i < 4; ++i) open_archive(i, argv[i+1]);
        case_first_strike(argv[5], argv[6]);
        return 0;
    } catch (const std::exception& error) { std::cerr << "FAIL " << error.what() << '\n'; return 1; }
}
