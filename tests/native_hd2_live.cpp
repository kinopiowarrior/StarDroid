// Exercises the same game renderer and optional pack switch as the Android JNI bridge.
#include "../native/runtime.cpp"
#include <array>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <set>

namespace fs = std::filesystem;
using namespace bwgame;
using Pixels = std::vector<uint32_t>;
constexpr int width = 640, height = 480;
static int checks = 0;
static void check(bool ok, const std::string& message) {
    ++checks;
    if (!ok) throw std::runtime_error(message);
}
static std::vector<uint8_t> world() {
    std::vector<uint8_t> bytes;
    native_save::Codec codec(false, bytes, g_ui->st);
    codec.world();
    return bytes;
}
static Pixels screen() {
    g_ui->external_minimap = true;
    openbw_render(0);
    check(openbw_framebuffer_width() == width && openbw_framebuffer_height() == height, "World viewport changed");
    Pixels result(width * height);
    std::memcpy(result.data(), openbw_framebuffer(), result.size() * sizeof(uint32_t));
    return result;
}
static Pixels crop(const Pixels& frame, xy position, int radius = 48) {
    const auto center = position - g_ui->screen_pos;
    Pixels result;
    for (int y = std::max(0, center.y - radius); y < std::min(height, center.y + radius); ++y) {
        int left = std::max(0, center.x - radius), right = std::min(width, center.x + radius);
        if (left < right) result.insert(result.end(), frame.begin()+y*width+left, frame.begin()+y*width+right);
    }
    return result;
}
static size_t different(const Pixels& a, const Pixels& b) {
    check(a.size() == b.size(), "Screens had different extents");
    size_t count = 0;
    for (size_t i=0; i<a.size(); ++i) count += a[i] != b[i];
    return count;
}
static void write_private_capture(const fs::path& path, const Pixels& frame) {
    std::ofstream f(path, std::ios::binary);
    f << "P6\n640 480\n255\n";
    for (auto p : frame) f.write(reinterpret_cast<const char*>(&p), 3);
    check(bool(f), "Could not save private capture");
}
static void center_on(const unit_t* unit) {
    g_ui->screen_pos = unit->position - xy(width/2, 180);
    g_ui->clamp_viewport();
}
static unit_t* spawn(UnitTypes type, xy pos, unsigned owner) {
    auto* unit = g_ui->create_completed_unit(g_ui->get_unit_type(type), pos, owner);
    check(unit && unit->sprite && unit->sprite->main_image, "Unit fixture could not spawn");
    return unit;
}
static const std::array<unsigned,5> image_ids{0, 5, 13, 122, 228};
static const std::array<UnitTypes,5> types{UnitTypes::Zerg_Scourge, UnitTypes::Zerg_Broodling,
    UnitTypes::Zerg_Defiler, UnitTypes::Protoss_Dragoon, UnitTypes::Terran_Ghost};
static void disable() { check(set_hd2_sprites("",false) == 0, "Could not switch to Classic"); }
static void enable(const fs::path& folder, int count) {
    check(set_hd2_sprites(folder.string(),true) == count, "Unexpected number of enabled unit bodies");
}
static void first_strike(const fs::path& media, const fs::path& pack, const fs::path& output) {
    enable(pack,5); // App Options may load the pack before starting any game.
    open_archive(3,media.string());
    openbw_init_campaign(width,height,201);
    g_ui->fog_player = -1;
    unit_t* dragoon = nullptr;
    for (auto& group : g_ui->st.player_units) for (auto* unit : ptr(group)) {
        if (g_ui->unit_is(unit, UnitTypes::Protoss_Dragoon) && !g_ui->unit_dying(unit)) { dragoon=unit; break; }
    }
    check(dragoon != nullptr, "Authored Dragoon missing from First Strike");
    center_on(dragoon);
    const auto preloaded = crop(screen(),dragoon->position);
    disable(); const auto classic = screen();
    const auto before = world();
    enable(pack,5); const auto hd = screen();
    check(preloaded == crop(hd,dragoon->position),"Pack loaded before campaign entry used a stale palette");
    const auto changed = different(crop(hd,dragoon->position),crop(classic,dragoon->position));
    check(changed >= 20, "Authored First Strike Dragoon did not change in the game framebuffer");
    check(before == world(), "Graphics switch or rendering changed simulation");
    if (!output.empty()) {
        write_private_capture(output/"first-strike-classic.ppm",classic);
        write_private_capture(output/"first-strike-hd2.ppm",hd);
        std::ofstream position(output/"first-strike-camera.txt");
        const auto at=dragoon->position-g_ui->screen_pos;
        position << at.x << ' ' << at.y << '\n';
    }
    std::cout << "PASS original First Strike Dragoon: " << changed << " changed framebuffer pixels\n";
    close_game();
}
static void skirmish(const fs::path& map, const fs::path& pack, const fs::path& fixtures) {
    open_archive(3,map.string()); openbw_init(width,height,1,0);
    g_ui->competitive=false; g_ui->fog_player=-1;
    g_ui->st.players[1].controller=player_t::controller_computer_game;
    g_ui->st.alliances[0][1]=g_ui->st.alliances[1][0]=1;
    const std::array<xy,5> points{xy{832,752}, xy{1000,750}, xy{1190,760}, xy{825,990}, xy{1040,980}};
    std::array<unit_t*,5> units{};
    std::array<Pixels,5> originals;
    for (size_t i=0;i<types.size();++i) {
        units[i]=spawn(types[i],points[i],1);
        check(size_t(units[i]->sprite->main_image->image_type->id) == image_ids[i], "Unexpected stock unit image identity");
    }
    center_on(units[0]); const auto on_entry = crop(screen(),units[0]->position);
    disable();
    for (size_t i=0;i<units.size();++i) {
        center_on(units[i]); originals[i]=crop(screen(),units[i]->position);
    }
    check(validate_hd2_sprites(pack.string())==5, "Valid pack rejected");
    enable(pack,5);
    for (size_t i=0;i<units.size();++i) {
        center_on(units[i]);
        const auto count=different(originals[i],crop(screen(),units[i]->position));
        check(count>=10,"Supported body failed to change framebuffer for image "+std::to_string(image_ids[i]));
        std::cout<<"PASS image "<<image_ids[i]<<": "<<count<<" changed pixels\n";
    }
    auto* scourge=units[0]; auto* image=scourge->sprite->main_image;
    center_on(scourge); const auto at=scourge->position;
    const auto original_hd=crop(screen(),at);
    check(original_hd == on_entry,"Starting a second map lost the imported pack or reused an old tileset palette");
    const auto flags=image->flags;
    image->flags ^= image_t::flag_horizontally_flipped;
    check(original_hd!=crop(screen(),at),"Flipped HD body has identical pixels");
    image->flags=flags;
    check(original_hd==crop(screen(),at),"Restoring flip changed HD frame");
    const auto color=g_ui->st.players[1].color;
    g_ui->st.players[1].color=(color+1)%8;
    check(crop(screen(),at)!=original_hd,"HD tint ignored player color change");
    g_ui->st.players[1].color=color;
    check(crop(screen(),at)==original_hd,"Restoring team color reused stale tint");

    const auto uid=g_ui->get_unit_id(scourge);
    auto saved=save_game(); const auto before=world();
    std::set<size_t> poses;
    std::set<Pixels> images;
    for(int i=0;i<32;++i) {
        native_step(); poses.insert(image->frame_index);
        if(i%4==0) images.insert(crop(screen(),scourge->position));
    }
    const auto continued=world();
    check(poses.size()>1 && images.size()>1,"Live iscript animation did not advance");
    restore_game(saved); check(world()==before,"World did not survive HD save restore");
    disable();
    for(int i=0;i<32;++i) {native_step(); if(i%4==0) screen();}
    check(world()==continued,"HD rendering changed deterministic simulation continuation");
    restore_game(saved); scourge=g_ui->get_unit(uid); check(scourge!=nullptr,"Saved Scourge missing");
    image=scourge->sprite->main_image; center_on(scourge);
    const auto classic=crop(screen(),at);
    enable(fixtures/"one",1);
    const auto partial_hd=crop(screen(),at);
    check(partial_hd==original_hd,"Partial pack changed retained Scourge pixels");
    auto* ghost=g_ui->get_unit_type(UnitTypes::Terran_Ghost);
    for(auto* unit : ptr(g_ui->st.player_units[1])) if(unit->unit_type==ghost) {
        center_on(unit);check(crop(screen(),unit->position)==originals[4],"Partial pack retained missing Ghost");break;
    }
    center_on(scourge);
    for(const char* invalid : {"broken","wrong"}) {
        const auto path=(fixtures/invalid).string();
        check(validate_hd2_sprites(path)<0 && set_hd2_sprites(path,true)<0,"Invalid replacement pack accepted");
        check(crop(screen(),at)==partial_hd,"Invalid replacement damaged active pack");
    }
    enable(fixtures/"empty",0);
    check(crop(screen(),at)==classic,"Empty pack retained stale graphics");
    const auto fallback=[&](const char* why){
        disable(); const auto reference=crop(screen(),at);enable(fixtures/"one",1);
        check(reference==crop(screen(),at),why);
    };
    const bool eud=g_ui->game_st.eud_enabled95;
    g_ui->st.game->eud_enabled95=true;fallback("EUD body was replaced");g_ui->st.game->eud_enabled95=eud;
    const bool raw=image->iscript_state.raw_mode98;
    image->iscript_state.raw_mode98=true;fallback("Raw iscript body was replaced");image->iscript_state.raw_mode98=raw;
    const auto* stock=image->grp; const auto frame=image->frame_index;
    image->grp=g_ui->global_st.image_grp[size_t(ImageTypes::IMAGEID_Marine)];image->frame_index=0;
    fallback("Custom GRP body was replaced");image->grp=stock;image->frame_index=frame;
    const auto modifier=image->modifier; const auto data1=image->modifier_data1, data2=image->modifier_data2;
    image->modifier=16;image->modifier_data1=0;image->modifier_data2=0;
    fallback("Hallucination modifier lost original renderer");
    image->modifier=modifier; image->modifier_data1=data1; image->modifier_data2=data2;
    const auto fy=image->frozen_y_value;
    image->frozen_y_value=g_ui->get_image_map_position(image).y;image->flags|=image_t::flag_y_frozen;
    fallback("Y-frozen image changed representation");image->frozen_y_value=fy;image->flags=flags;
    for (xy corner : {xy(6,6),xy(634,6),xy(6,474),xy(634,474)}) {
        const auto p=g_ui->get_image_center_map_position(image);
        g_ui->screen_pos=p-corner;
        disable();const auto clipped=crop(screen(),p);enable(fixtures/"one",1);
        check(different(clipped,crop(screen(),p))>0,"Clipped body disappeared near viewport edge");
    }
    center_on(scourge);
    const auto old_visibility=scourge->sprite->visibility_flags;
    g_ui->fog_player=0;
    for(int mode:{1,2}) {
        for(auto& tile:g_ui->st.tiles){tile.visible=255;tile.explored= mode==1 ? 0 : 255;}
        scourge->sprite->visibility_flags=0;
        check(g_ui->unit_hidden_by_fog(scourge),"Fog fixture did not hide enemy body");
        fallback(mode==1 ? "HD body leaked into explored fog" : "HD body leaked into shroud");
    }
    for(auto& tile:g_ui->st.tiles){tile.visible=0;tile.explored=0;}
    scourge->sprite->visibility_flags=255;
    const auto unitflags=scourge->status_flags;const auto detected=scourge->detected_flags;
    scourge->status_flags|=unit_t::status_flag_cloaked|unit_t::status_flag_requires_detector;
    scourge->detected_flags=0;
    check(g_ui->unit_has_cloak_shimmer(scourge),"Cloak fixture did not shimmer");
    fallback("HD pixels replaced native cloak refraction");
    scourge->status_flags=unitflags;scourge->detected_flags=detected;
    scourge->sprite->visibility_flags=old_visibility;g_ui->fog_player=-1;

    // The foreground Wraith's classic opaque pixels must occlude the HD Scourge.
    auto* wraith=spawn(UnitTypes::Terran_Wraith,at+xy(0,8),0);
    auto* foreground=wraith->sprite->main_image;
    const auto wf=foreground->flags, sf=image->flags;
    const auto both=crop(screen(),at);
    image->flags|=image_t::flag_hidden;const auto only_front=crop(screen(),at);
    foreground->flags|=image_t::flag_hidden;const auto neither=crop(screen(),at);
    image->flags=sf;const auto only_back=crop(screen(),at);foreground->flags=wf;
    size_t overlap=0;
    for(size_t i=0;i<both.size();++i) if(only_front[i]!=neither[i] && only_back[i]!=neither[i]) {
        ++overlap;check(both[i]==only_front[i],"HD Scourge painted over classic foreground Wraith");
    }
    check(overlap>=10,"Foreground Wraith failed to overlap HD body");
    std::cout<<"PASS classic foreground occlusion: "<<overlap<<" pixels; iscript poses: "<<poses.size()<<"\n";
    disable();close_game();
}
int main(int argc,char** argv) {
    try {
        check(argc==8 || argc==9,"Usage: hd2-live STARDAT BROODAT PATCH STARCRAFT WEAVE_MAP ANIM_DIR FIXTURES [PRIVATE_CAPTURES]");
        for(int i=0;i<3;++i) open_archive(i,argv[i+1]);
        first_strike(argv[4],argv[6],argc==9?fs::path(argv[8]):fs::path{});
        skirmish(argv[5],argv[6],argv[7]);
        std::cout<<"RESULT {\"passed\":true,\"checks\":"<<checks<<",\"supportedBodies\":5,\"mission\":\"First Strike\"}\n";
        return 0;
    } catch(const std::exception& error) {std::cerr<<"FAIL "<<error.what()<<'\n';return 1;}
}
