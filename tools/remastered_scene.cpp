// Host-only, body-only HD2 experiment. This file is not compiled into Android.
// The source root for runtime.cpp is supplied by tests/run_remastered_scene.py.
#include "runtime.cpp"
#include "../native/remastered/assets.h"
#include "../native/remastered/geometry.h"
#include <filesystem>
#include <fstream>
#include <iostream>
#include <set>

using namespace bwgame;
namespace scr = stardroid::remastered;
namespace fs = std::filesystem;
static int checks = 0;
static void need(bool value, const char* message) { ++checks; if (!value) throw std::runtime_error(message); }
static std::vector<uint8_t> world() { std::vector<uint8_t> out; native_save::Codec c(false, out, g_ui->st); c.world(); return out; }
static std::vector<uint8_t> read_asset(const fs::path& p) {
    const auto n = fs::file_size(p); if (n > scr::Limits{}.max_file_bytes) throw std::runtime_error("Asset too large");
    std::vector<uint8_t> out(n); std::ifstream f(p, std::ios::binary);
    if (!f.read(reinterpret_cast<char*>(out.data()), n)) throw std::runtime_error("Cannot read asset"); return out;
}
static void pam(const fs::path& p, const scr::Image& im) {
    std::ofstream f(p, std::ios::binary); f << "P7\nWIDTH " << im.width << "\nHEIGHT " << im.height << "\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n";
    f.write(reinterpret_cast<const char*>(im.rgba.data()), im.rgba.size()); if (!f) throw std::runtime_error("Cannot write preview");
}

// This deliberately renders only the selected fixture units' main body images.
// Shadows, overlays, HUD, selection circles, and lighting are outside this tool.
// draw_sprites remains the engine implementation: sorting, live-unit visibility,
// hidden image flags and cloak/fog decisions are not rebuilt in a second model.
struct BodyScene : ui_functions {
    std::set<const sprite_t*> fixture;
    const scr::DecodedSprite* asset = nullptr;
    const std::vector<scr::Frame>* geometry = nullptr;
    bool enabled = false, reference = false, force_classic = false;
    scr::Image result;
    std::vector<uint8_t> indices, hd_mask;
    std::vector<size_t> order;
    size_t hd_draws = 0, classic_draws = 0, classic_over_hd = 0;
    // Overrides are for isolated color-mask tests only. Normal scene rendering
    // uses shade 5 (index 4) of the owner's actual eight-color classic unit ramp
    // as a representative RGB tint. This is not the retail Remastered color table.
    std::optional<std::array<uint8_t, 3>> preview_tint;
    std::array<uint8_t, 3> image_tint(const image_t* im) const {
        if (preview_tint) return *preview_tint;
        const unsigned owner = im->sprite->owner;
        const size_t color = display_player_color(st.players[owner].color, owner);
        const auto palette = unit_player_colors101(owner, color);
        const size_t at = size_t(palette[4]) * 4;
        return {tileset_img.wpe[at], tileset_img.wpe[at + 1], tileset_img.wpe[at + 2]};
    }

    explicit BodyScene(game_player proxy) : ui_functions(std::move(proxy)) {
        img = g_ui->img; tileset_img = g_ui->tileset_img;
        creep_random_tile_indices = g_ui->creep_random_tile_indices;
        screen_pos = g_ui->screen_pos; screen_width = 640; screen_height = 480;
        view_width = screen_width; view_height = screen_height; view_scale = fp16::integer(1);
        draw_ui_elements = false; fog_player = -1;
    }
    static std::unique_ptr<BodyScene> create() {
        game_player proxy; proxy.set_st(g_ui->st); return std::make_unique<BodyScene>(std::move(proxy));
    }
    void palette_pixel(size_t x, size_t y, uint8_t index) {
        for (unsigned dy = 0; dy < 2; ++dy) for (unsigned dx = 0; dx < 2; ++dx) {
            const size_t at = (y * 2 + dy) * result.width + x * 2 + dx;
            for (unsigned c = 0; c < 3; ++c) result.rgba[at * 4 + c] = tileset_img.wpe[size_t(index) * 4 + c];
            result.rgba[at * 4 + 3] = 255;
            if (hd_mask[at]) ++classic_over_hd;
            hd_mask[at] = 0;
        }
    }
    void upscale() {
        for (size_t y = 0; y < screen_height; ++y) for (size_t x = 0; x < screen_width; ++x)
            palette_pixel(x, y, indices[y * screen_width + x]);
    }
    bool eligible(const image_t* im) const {
        return enabled && asset && geometry && !force_classic && !game_st.eud_enabled95 &&
            im->image_type->id == ImageTypes::IMAGEID_Scourge &&
            im->grp == global_st.image_grp[size_t(ImageTypes::IMAGEID_Scourge)] &&
            !im->iscript_state.raw_mode98 && !(im->flags & image_t::flag_y_frozen) &&
            (im->modifier == 0 || im->modifier == 1) && asset->scale() == 2 &&
            im->grp->frames.size() == asset->frames().size() && geometry->size() == asset->frames().size() && im->frame_index < asset->frames().size();
    }
    void draw_hd(const image_t* im) {
        const auto b = asset->frame_bounds(im->frame_index);
        const auto tint = image_tint(im);
        const auto& diffuse = asset->diffuse(); const auto* team = asset->teamcolor();
        const bool flip = (im->flags & image_t::flag_horizontally_flipped) != 0;
        const xy center = get_image_center_map_position(im) - screen_pos;
        const auto placement = scr::place_frame_hd2(geometry->at(im->frame_index), asset->canvas_width_hd(),
            asset->canvas_height_hd(), center.x, center.y, flip);
        const int left = placement.left, top = placement.top;
        for (uint32_t y = 0; y < b.height; ++y) for (uint32_t x = 0; x < b.width; ++x) {
            const int ox = left + int(x), oy = top + int(y);
            if (ox < 0 || oy < 0 || ox >= int(result.width) || oy >= int(result.height)) continue;
            const size_t sx = b.tex_x + (flip ? b.width - 1 - x : x), sy = b.tex_y + y;
            const size_t src = (sy * diffuse.width + sx) * 4, dst = size_t(oy) * result.width + ox;
            const unsigned alpha = diffuse.rgba[src + 3]; if (!alpha) continue;
            const unsigned mask = team ? team->rgba[(sy * team->width + sx) * 4] : 0;
            for (unsigned c = 0; c < 3; ++c) {
                unsigned color = diffuse.rgba[src + c];
                color = (color * (255 * (255 - mask) + tint[c] * mask) + 32512) / 65025;
                result.rgba[dst * 4 + c] = uint8_t((color * alpha + result.rgba[dst * 4 + c] * (255 - alpha) + 127) / 255);
            }
            result.rgba[dst * 4 + 3] = 255; hd_mask[dst] = 1;
        }
        ++hd_draws;
    }
    void draw_classic(const image_t* im, bool shimmer = false) {
        draw_image(im, indices.data(), screen_width, st.players[im->sprite->owner].color, shimmer);
        ++classic_draws;
        if (reference || force_classic) return;
        const auto& frame = im->grp->frame_at105(im->frame_index);
        if (!frame.size.x || !frame.size.y) return;
        std::vector<uint8_t> mask(frame.size.x * frame.size.y);
        // An explicit write mask preserves occlusion even if an opaque classic
        // pixel has the same index as its underlying terrain. A pixel diff does not.
        draw_frame(frame, bool(im->flags & image_t::flag_horizontally_flipped), mask.data(), frame.size.x,
            0, 0, frame.size.x, frame.size.y, [](uint8_t, uint8_t) { return uint8_t(1); });
        const xy at = get_image_map_position(im) - screen_pos;
        for (size_t y = 0; y < frame.size.y; ++y) for (size_t x = 0; x < frame.size.x; ++x) {
            if (!mask[y * frame.size.x + x]) continue;
            const int ox = at.x + int(x), oy = at.y + int(y);
            if (ox < 0 || oy < 0 || ox >= int(screen_width) || oy >= int(screen_height)) continue;
            palette_pixel(ox, oy, indices[size_t(oy) * screen_width + ox]);
        }
    }
    void draw_sprite(const sprite_t* s, uint8_t*, size_t) override {
        if (!fixture.count(s)) return;
        const auto* im = s->main_image;
        if (!im || (im->flags & image_t::flag_hidden)) return;
        order.push_back(s->index);
        if (!reference && eligible(im)) draw_hd(im); else draw_classic(im, cloak_shimmer_sprite.at(s->index));
    }
    void draw_fog_snapshot(const sprite_t* s, uint8_t*, size_t) override {
        if (!fixture.count(s)) return;
        throw std::runtime_error("Body-only fixture does not support remembered buildings");
    }
    int light_at(int sx, int sy) {
        const auto tb = screen_tile_bounds();
        const int bx = int(tb.from.x) - 1, by = int(tb.from.y) - 1;
        const int gw = int(tb.to.x - tb.from.x) + 3, gh = int(tb.to.y - tb.from.y) + 3;
        const int mx = screen_pos.x + sx - 16, my = screen_pos.y + sy - 16;
        const int tx = mx >= 0 ? mx / 32 : -((-mx + 31) / 32), ty = my >= 0 ? my / 32 : -((-my + 31) / 32);
        const int fx = mx - tx * 32, fy = my - ty * 32;
        const int gx = std::max(0, std::min(gw - 2, tx - bx)), gy = std::max(0, std::min(gh - 2, ty - by));
        const auto* a = fog_level_grid.data() + gy * gw + gx; const auto* b = a + gw;
        return ((a[0] * (32 - fx) + a[1] * fx) * (32 - fy) + (b[0] * (32 - fx) + b[1] * fx) * fy) >> 10;
    }
    scr::Image render() {
        order.clear(); hd_draws = classic_draws = classic_over_hd = 0;
        force_classic = game_st.eud_enabled95;
        // Background-reading modifiers need an RGBA effect implementation. Until
        // then the whole body fixture uses the original indexed pipeline.
        for (auto* s : fixture) if (s->main_image && s->main_image->modifier != 0 && s->main_image->modifier != 1)
            force_classic = true;
        for (auto& units : st.player_units) for (auto* u : ptr(units))
            if (fixture.count(u->sprite) && unit_has_cloak_shimmer(u)) force_classic = true;
        indices.assign(screen_width * screen_height, 0);
        result = {uint32_t(screen_width * 2), uint32_t(screen_height * 2), std::vector<uint8_t>(screen_width * screen_height * 16)};
        hd_mask.assign(result.width * result.height, 0);
        draw_tiles(indices.data(), screen_width); upscale();
        draw_sprites(indices.data(), screen_width);
        if (fog_player >= 0 || (game_st.eud_enabled95 && uint8_t(st.eud95.game_brightness97) != 31)) {
            draw_fog(indices.data(), screen_width);
            for (size_t y = 0; y < screen_height; ++y) for (size_t x = 0; x < screen_width; ++x) {
                const int light = light_at(x, y); const uint8_t index = indices[y * screen_width + x];
                for (unsigned dy = 0; dy < 2; ++dy) for (unsigned dx = 0; dx < 2; ++dx) {
                    const size_t at = (y * 2 + dy) * result.width + x * 2 + dx;
                    for (unsigned c = 0; c < 3; ++c)
                        result.rgba[at * 4 + c] = hd_mask[at] ? uint8_t((unsigned(result.rgba[at * 4 + c]) * light + 15) / 31) : tileset_img.wpe[size_t(index) * 4 + c];
                }
            }
        }
        if (reference || force_classic) upscale();
        return result;
    }
};

static unit_t* spawn(UnitTypes id, xy where, int owner) {
    auto* u = g_ui->create_completed_unit(g_ui->get_unit_type(id), where, owner);
    need(u && u->sprite && u->sprite->main_image, "Fixture unit creation failed"); return u;
}
static void visible(int mode) {
    for (auto& t : g_ui->st.tiles) { t.visible = mode == 0 ? 0 : 255; t.explored = mode == 2 ? 255 : 0; }
}
int main(int argc, char** argv) {
    try {
        std::cout.setf(std::ios::unitbuf);
        need(argc == 7, "Usage: remastered-scene STARDAT BROODAT PATCH MAP main_000.anim OUTPUT");
        open_archive(0, argv[1]); open_archive(1, argv[2]); open_archive(2, argv[3]); open_archive(3, argv[4]);
        openbw_init(640, 480, 1, 0); g_ui->competitive = false;
        g_ui->st.players[1].controller = player_t::controller_computer_game;
        g_ui->screen_pos = {512, 512};
        auto anim = scr::Anim::parse(read_asset(argv[5]));
        auto asset = anim.decode_sprite();
        need(asset.scale() == 2 && asset.frames().size() == 85, "Expected original HD2 Scourge with85 frames");
        auto* scourge = spawn(UnitTypes::Zerg_Scourge, {832, 752}, 1);
        auto* wraith = spawn(UnitTypes::Terran_Wraith, {832, 760}, 0);
        auto scene = BodyScene::create(); scene->asset = &asset; scene->geometry = &anim.frames(); scene->enabled = true;
        scene->fixture = {scourge->sprite, wraith->sprite};
        const fs::path output(argv[6]); fs::create_directories(output);
        const auto before = world(); const auto first = scene->render();
        need(world() == before, "Rendering changed synchronized world");
        need(scene->hd_draws == 1 && scene->classic_draws == 1, "Expected one HD and one classic body");
        need(scene->classic_over_hd > 0, "Classic Wraith did not exercise occlusion over HD Scourge");
        const size_t overlap = scene->classic_over_hd;
        const auto draw_order = scene->order;
        need(draw_order.size() == 2 && draw_order[0] == scourge->sprite->index && draw_order[1] == wraith->sprite->index, "Engine sorted order not followed");
        scene->reference = true; const auto classic = scene->render(); scene->reference = false;
        need(scene->order == draw_order, "Classic and HD draw orders differ");
        // Every final non-HD pixel must exactly equal independent indexed rendering.
        // The Scourge's different silhouette may expose terrain instead of its old
        // classic body, so test opaque Wraith coverage separately below.
        scene->fixture = {wraith->sprite}; scene->reference = true; const auto only_wraith = scene->render(); scene->reference = false;
        scene->fixture.clear(); scene->reference = true; const auto terrain = scene->render(); scene->reference = false;
        size_t occlusion_checked = 0;
        for (size_t i = 0; i < first.rgba.size(); i += 4) if (only_wraith.rgba[i] != terrain.rgba[i] || only_wraith.rgba[i+1] != terrain.rgba[i+1] || only_wraith.rgba[i+2] != terrain.rgba[i+2]) {
            need(std::equal(first.rgba.begin()+i, first.rgba.begin()+i+4, only_wraith.rgba.begin()+i), "Classic foreground body lost occlusion"); ++occlusion_checked;
        }
        need(occlusion_checked > 0, "No classic foreground pixels checked");
        scene->fixture = {scourge->sprite, wraith->sprite};
        pam(output / "scene-classic.pam", classic); pam(output / "scene-hd2.pam", first);
        scene->asset = nullptr; need(scene->render().rgba == classic.rgba, "Missing asset fallback differs from indexed reference"); scene->asset = &asset;
        scene->enabled = false; need(scene->render().rgba == classic.rgba, "Disabled preview differs from indexed reference"); scene->enabled = true;
        scene->fixture = {scourge->sprite};
        const auto owner_image = scene->render();
        const auto owner_color = g_ui->st.players[scourge->owner].color;
        const auto owner_tint = scene->image_tint(scourge->sprite->main_image);
        g_ui->st.players[scourge->owner].color = owner_color == 0 ? 1 : 0;
        need(scene->image_tint(scourge->sprite->main_image) != owner_tint && scene->render().rgba != owner_image.rgba,
            "Changing the owner's player color did not change HD tint");
        g_ui->st.players[scourge->owner].color = owner_color;
        need(scene->render().rgba == owner_image.rgba, "Restoring the owner's color did not restore HD pixels");
        scene->preview_tint = std::array<uint8_t,3>{255,40,20}; const auto red = scene->render();
        scene->preview_tint = std::array<uint8_t,3>{30,80,255}; const auto blue = scene->render();
        need(red.rgba != blue.rgba, "Team-color mask had no effect");
        const auto flags = scourge->sprite->main_image->flags;
        scourge->sprite->main_image->flags ^= image_t::flag_horizontally_flipped;
        const auto flipped = scene->render(); need(flipped.rgba != blue.rgba, "Flipped frame did not change rendered pixels");
        scourge->sprite->main_image->flags = flags;
        scene->preview_tint.reset();
        pam(output / "scourge-red.pam", red); pam(output / "scourge-blue.pam", blue); pam(output / "scourge-flipped.pam", flipped);
        auto* im = scourge->sprite->main_image; const auto modifier = im->modifier;
        const auto modifier_data1 = im->modifier_data1, modifier_data2 = im->modifier_data2;
        im->modifier = 16; im->modifier_data1 = im->modifier_data2 = 0;
        scene->reference = true; const auto effect_reference = scene->render(); scene->reference = false;
        need(scene->render().rgba == effect_reference.rgba && scene->hd_draws == 0, "Unsupported modifier did not use classic pipeline"); im->modifier = modifier; im->modifier_data1 = modifier_data1; im->modifier_data2 = modifier_data2;
        const auto* grp = im->grp; const auto frame = im->frame_index;
        im->grp = g_ui->global_st.image_grp[size_t(ImageTypes::IMAGEID_Marine)]; im->frame_index = 0;
        scene->reference = true; const auto grp_reference = scene->render(); scene->reference = false;
        need(scene->render().rgba == grp_reference.rgba && scene->hd_draws == 0, "Changed GRP did not fall back"); im->grp = grp; im->frame_index = frame;
        const bool eud = g_ui->game_st.eud_enabled95; g_ui->st.game->eud_enabled95 = true;
        scene->reference = true; const auto eud_reference = scene->render(); scene->reference = false;
        need(scene->render().rgba == eud_reference.rgba && scene->hd_draws == 0, "EUD map did not fall back after caching"); g_ui->st.game->eud_enabled95 = eud;
        visible(0); scene->fog_player = 0; scourge->sprite->visibility_flags = 255;
        need(scene->render().rgba != terrain.rgba && scene->hd_draws == 1, "Visible enemy body missing");
        scourge->sprite->visibility_flags = 0; visible(1);
        const auto fogged = scene->render(); need(scene->hd_draws == 0 && scene->classic_draws == 0, "Hidden enemy leaked into explored fog");
        scene->fixture.clear(); const auto fog_terrain = scene->render(); need(fogged.rgba == fog_terrain.rgba, "Hidden enemy pixels remain in explored fog");
        scene->fixture = {scourge->sprite}; visible(2); const auto shroud = scene->render();
        need(scene->hd_draws == 0 && scene->classic_draws == 0, "Enemy body leaked into shroud");
        scene->fixture.clear(); need(shroud.rgba == scene->render().rgba, "Shroud retains enemy pixels");
        scene->fixture = {scourge->sprite}; visible(0); scourge->sprite->visibility_flags = 255;
        pam(output / "scourge-visible.pam", scene->render()); pam(output / "scourge-fog-hidden.pam", fogged);
        // Rendering the same frame repeatedly never advances animation/simulation.
        const auto state_before = world(); const auto stable = scene->render();
        need(scene->render().rgba == stable.rgba && world() == state_before, "Repeated rendering changed pixels or synchronized world");
        // Native cloak shimmer is an unsupported HD effect: retain its real
        // classic silhouette/refraction instead of dropping the visible cue.
        const auto status = scourge->status_flags; const auto detected = scourge->detected_flags;
        scourge->status_flags |= unit_t::status_flag_cloaked | unit_t::status_flag_requires_detector;
        scourge->detected_flags = 0;
        scene->reference = true; const auto cloak_ref = scene->render(); scene->reference = false;
        need(scene->render().rgba == cloak_ref.rgba && scene->hd_draws == 0 && scene->classic_draws == 1,
            "Cloak shimmer did not use original classic refraction");
        scourge->status_flags = status; scourge->detected_flags = detected;
        // Clip the same asset on every side, including odd camera coordinates.
        scene->fog_player = -1;
        for (xy center : {xy(0,0), xy(639,0), xy(0,479), xy(639,479), xy(317,239)}) {
            scene->screen_pos = scourge->sprite->position - center;
            const auto camera_state = world(); const auto clipped = scene->render();
            need(scene->hd_draws == 1 && std::count(scene->hd_mask.begin(),scene->hd_mask.end(),uint8_t(1)) > 0,
                "Edge clipping removed the whole HD body");
            need(world() == camera_state && clipped.width == 1280 && clipped.height == 960,
                "Camera preview changed simulation or render dimensions");
        }
        scene->screen_pos = {512,512}; scene->preview_tint.reset();
        g_ui->st.alliances[0][1] = g_ui->st.alliances[1][0] = 1;
        std::set<size_t> animated_frames;
        for (int capture = 0; capture < 12; ++capture) {
            for (int step = 0; step < 4; ++step) native_step();
            animated_frames.insert(scourge->sprite->main_image->frame_index);
            const auto stepped_world = world(); const auto hd = scene->render();
            scene->reference = true; const auto sd = scene->render(); scene->reference = false;
            need(world() == stepped_world, "Animation capture changed synchronized world");
            const std::string number = capture < 10 ? "0" + std::to_string(capture) : std::to_string(capture);
            pam(output / ("animation-hd2-" + number + ".pam"), hd);
            pam(output / ("animation-classic-" + number + ".pam"), sd);
        }
        need(animated_frames.size() > 1, "Real engine animation did not advance to another frame");
        std::cout << "OWNER_TINT " << unsigned(owner_tint[0]) << "," << unsigned(owner_tint[1]) << "," << unsigned(owner_tint[2]) << '\n';
        std::cout << "ANIMATION_FRAMES " << animated_frames.size() << '\n';
        scene.reset(); close_game();
        std::cout << "RESULT {\"passed\":true,\"checks\":" << checks << ",\"classicOcclusionPixels\":" << occlusion_checked << ",\"classicWritesOverHd\":" << overlap << ",\"output\":[1280,960],\"worldViewport\":[640,480],\"scope\":\"Host-only body scene; no Android integration, shadows, overlays, HUD or Remastered lighting\"}\n";
        return 0;
    } catch (const std::exception& e) { std::cerr << "FAIL " << e.what() << '\n'; return 1; }
}
