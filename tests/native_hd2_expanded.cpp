// Stock image entry points, authored missions and independent shadow reference frames.
#include "../native/runtime.cpp"
#include <array>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <set>

namespace fs = std::filesystem;
using namespace bwgame;
using Pixels = std::vector<uint32_t>;
constexpr int W=640, H=480;
static const std::array<unsigned,29> ids={0,1,2,3,4,5,6,7,13,24,36,45,63,81,100,122,145,177,202,228,325,348,527,545,723,747,758,862,893};
static void require(bool value,const std::string& reason){if(!value)throw std::runtime_error(reason);}
static std::vector<uint8_t> world(){std::vector<uint8_t> bytes;native_save::Codec codec(false,bytes,g_ui->st);codec.world();return bytes;}
static Pixels frame(int density=2) {
    Pixels result(size_t(W)*H*density*density);
    const auto before=g_ui->st.current_frame;const int tick=g_ui->event_tick;
    render_world_pixels(reinterpret_cast<uint8_t*>(result.data()),size_t(W)*density*4,W*density,H*density,0);
    require(g_ui->st.current_frame==before && g_ui->event_tick==tick+1,"Render changed presentation or simulation timing");
    return result;
}
static void classic(){require(set_hd2_sprites("",false)==0,"Could not select Classic");}
static void load(const fs::path& directory,int count){require(set_hd2_sprites(directory.string(),true)==count,"Unexpected sprite count in "+directory.string());}
static size_t diff(const Pixels& a,const Pixels& b){
    require(a.size()==b.size(),"Output sizes differ");size_t n=0;
    for(size_t k=0;k<a.size();++k)n+=a[k]!=b[k];return n;
}
static void center(xy point){g_ui->screen_pos=point-xy(W/2,H/2);g_ui->clamp_viewport();}
static image_t* find_image(sprite_t* s,unsigned id){
    for(auto* im:ptr(s->images))if(unsigned(im->image_type->id)==id)return im;
    return nullptr;
}
static unit_t* spawn(UnitTypes type,xy pos){
    auto* u=g_ui->create_completed_unit(g_ui->get_unit_type(type),pos,1);
    require(u&&u->sprite&&u->sprite->main_image,"Unit spawn failed");return u;
}
static unit_t* placed(UnitTypes type){
    for(auto& group:g_ui->st.player_units)for(auto* u:ptr(group))
        if(g_ui->unit_is(u,type)&&!g_ui->unit_dying(u)&&u->sprite)return u;
    return nullptr;
}
static Pixels region(const Pixels& src,xy point,int rx,int ry){
    const auto at=point-g_ui->screen_pos;Pixels result;
    for(int y=2*std::max(0,at.y-ry);y<2*std::min(H,at.y+ry);++y){
        const int left=2*std::max(0,at.x-rx),right=2*std::min(W,at.x+rx);
        if(left<right)result.insert(result.end(),src.begin()+size_t(y)*W*2+left,src.begin()+size_t(y)*W*2+right);
    }
    return result;
}
static void ppm(const fs::path& p,const Pixels& data,int w,int h){
    require(data.size()==size_t(w)*h,"Mismatched capture dimensions");std::ofstream f(p,std::ios::binary);
    f<<"P6\n"<<w<<' '<<h<<"\n255\n";std::vector<char> row(w*3);
    for(int y=0;y<h;++y){for(int x=0;x<w;++x)std::memcpy(&row[x*3],&data[size_t(y)*w+x],3);f.write(row.data(),row.size());}
    require(bool(f),"Capture write failed");
}
static fs::path single(const fs::path& root,unsigned id){return root/std::to_string(id);}
static void fog(bool seen,bool explored){
    g_ui->fog_player=0;
    for(auto& tile:g_ui->st.tiles){tile.visible=seen?0:255;tile.explored=explored?0:255;}
}

static void campaigns(const fs::path& media,const fs::path& singles,const fs::path& captures){
    open_archive(3,media.string());openbw_init_campaign(W,H,101);
    auto* larva=placed(UnitTypes::Zerg_Larva);
    for(int i=0;!larva&&i<128;++i){native_step();larva=placed(UnitTypes::Zerg_Larva);}
    require(larva,"Starting Hatchery did not produce a Larva");g_ui->fog_player=-1;center(larva->position);
    classic();const auto classic_larva=region(frame(),larva->position,36,36);
    load(single(singles,36),1);const auto hd_larva=region(frame(),larva->position,36,36);
    require(diff(hd_larva,classic_larva)>40,"Authored Zerg mission Larva remained classic");
    std::set<size_t> poses;for(int i=0;i<96;++i){native_step();poses.insert(larva->sprite->main_image->frame_index);}
    require(poses.size()>1,"Real Larva animation did not advance");
    std::cout<<"PASS Among the Ruins: Larva shows HD2 and advances "<<poses.size()<<" animation poses\n";
    close_game();

    open_archive(3,media.string());openbw_init_campaign(W,H,2);
    auto* cc=placed(UnitTypes::Zerg_Infested_Command_Center);
    require(cc,"Backwater Station has no Infested Command Center");g_ui->fog_player=-1;center(cc->position);
    classic();const auto base=frame();
    load(single(singles,63),1);const auto hd=frame();
    require(diff(region(hd,cc->position,105,90),region(base,cc->position,105,90))>500,"Authored Infested Command Center retained classic art");
    ppm(captures/"building-classic.ppm",base,W*2,H*2);ppm(captures/"building-hd2.ppm",hd,W*2,H*2);
    const auto at=cc->position-g_ui->screen_pos;
    std::ofstream(captures/"building.txt")<<at.x<<' '<<at.y<<'\n';
    fog(true,true);cc->sprite->visibility_flags=255;frame();
    fog(false,true);cc->sprite->visibility_flags=0;
    require(g_ui->unit_frozen_by_fog(cc),"Authored CC did not enter explored fog");
    const xy location=cc->position;const auto remembered=region(frame(),location,110,96);
    classic();const auto fog_classic=region(frame(),location,110,96);
    require(diff(remembered,fog_classic)>200,"Remembered HD building reverted to classic");
    load(single(singles,63),1);
    auto* body=cc->sprite->main_image;const auto original_frame=body->frame_index;
    const auto original_owner=cc->sprite->owner;
    const auto original_color=g_ui->st.players[original_owner].color;const auto original_offset=body->offset;
    body->frame_index=(original_frame+1)%body->grp->frames.size();body->offset+=xy(19,9);
    cc->sprite->owner=(original_owner+1)%8;g_ui->st.players[original_owner].color=(original_color+1)%8;
    const auto original_position=cc->sprite->position;
    g_ui->move_sprite(cc->sprite,original_position+xy(-480,670)); // another tile list, outside the camera
    require(region(frame(),location,110,96)==remembered,"Live position, pose, or tint leaked through explored fog");
    g_ui->move_sprite(cc->sprite,original_position);
    body->frame_index=original_frame;body->offset=original_offset;cc->sprite->owner=original_owner;g_ui->st.players[original_owner].color=original_color;
    auto saved=save_game();restore_game(saved);
    cc=placed(UnitTypes::Zerg_Infested_Command_Center);
    require(cc && g_ui->unit_frozen_by_fog(cc),"Restored building lost fog state");
    require(region(frame(),location,110,96)==fog_classic,"Save without a serializable HD observation should replay the original frozen GRP");
    fog(true,true);cc->sprite->visibility_flags=255;frame();
    fog(false,true);cc->sprite->visibility_flags=0;
    require(region(frame(),location,110,96)==remembered,"Reacquired building did not resume its HD2 fog memory");
    auto& memory=g_ui->fog_snapshots.at(cc->sprite->index);
    const auto original_memory=memory;memory.valid=false;
    const auto unseen=region(frame(),location,110,96);
    classic();require(region(frame(),location,110,96)==unseen && unseen!=fog_classic,"Unobserved building leaks through explored fog");
    load(single(singles,63),1);memory=original_memory;
    ++cc->unit_id_generation;
    require(region(frame(),location,110,96)==unseen,"Recycled unit generation inherited a hidden building's image");
    --cc->unit_id_generation;memory=original_memory;
    // Leave all adjacent tiles explored, so final fog shading cannot mask a body leaking at the edges.
    auto& anchor_tile=g_ui->st.tiles.at(g_ui->tile_index(location));anchor_tile.explored=255;
    const auto edge_shroud=region(frame(),location,110,96);memory.valid=false;
    require(region(frame(),location,110,96)==edge_shroud,"Remembered body protrudes into explored tiles from a shrouded center");
    anchor_tile.explored=0;memory=original_memory;
    g_ui->seed_explored_structures();
    require(region(frame(),location,110,96)==fog_classic && !memory.observed.type,
            "Pre-explored CHK map seed disclosed a hidden HD pose");
    memory=original_memory;
    fog(false,false);const auto black=region(frame(),location,110,96);
    classic();require(black==region(frame(),location,110,96),"HD building leaks into unexplored shroud");
    std::cout<<"PASS Backwater Station: placed building, fog protects hidden movement, pose, tint, save restore and reacquisition; no shroud or unobserved-unit leak\n";
    close_game();
}

static sprite_t* original_sprite(unsigned image,xy pos){
    for(int i=0;i<517;++i){
        auto* type=g_ui->get_sprite_type(SpriteTypes(i));
        if(unsigned(type->image->id)==image)return g_ui->create_sprite(type,pos,1);
    }
    return nullptr;
}
static image_t* fixture(unsigned id,xy pos){
    const std::pair<unsigned,UnitTypes> stock[]={
        {0,UnitTypes::Zerg_Scourge},{1,UnitTypes::Zerg_Scourge},{5,UnitTypes::Zerg_Broodling},{6,UnitTypes::Zerg_Broodling},
        {13,UnitTypes::Zerg_Defiler},{36,UnitTypes::Zerg_Larva},{63,UnitTypes::Zerg_Infested_Command_Center},
        {81,UnitTypes::Zerg_Greater_Spire},{100,UnitTypes::Zerg_Spore_Colony},{122,UnitTypes::Protoss_Dragoon},
        {145,UnitTypes::Protoss_Reaver},{177,UnitTypes::Protoss_Cybernetics_Core},{202,UnitTypes::Protoss_Stargate},
        {228,UnitTypes::Terran_Ghost},{348,UnitTypes::Resource_Mineral_Field}
    };
    for(const auto& item:stock)if(item.first==id){
        auto* u=spawn(item.second,pos);auto* im=find_image(u->sprite,id);
        require(im,"Native stock unit did not create image "+std::to_string(id));return im;
    }
    if(id==893){
        auto* s=original_sprite(892,pos);require(s,"Missing desert doodad shadow parent");
        auto* im=find_image(s,id);require(im,"Missing desert doodad shadow");return im;
    }
    if(auto* s=original_sprite(id,pos))return s->main_image;
    auto* unit=spawn(id==2?UnitTypes::Zerg_Egg:UnitTypes::Terran_Marine,pos);
    return g_ui->create_image(g_ui->get_image_type(ImageTypes(id)),unit->sprite,{},g_ui->image_order_top);
}
static void skirmish(const fs::path& map,const fs::path& all,const fs::path& singles,const fs::path& captures){
    open_archive(3,map.string());openbw_init(W,H,1,0);g_ui->competitive=false;
    g_ui->st.players[1].controller=player_t::controller_computer_game;
    g_ui->st.alliances[0][1]=g_ui->st.alliances[1][0]=1;
    g_ui->fog_player=-1;classic();const xy pos{1032,761};center(pos);frame();
    const auto pristine=save_game();
    for(unsigned id:ids){
        auto saved=pristine;restore_game(saved);g_ui->fog_player=-1;center(pos);
        auto* im=fixture(id,pos);require(im && unsigned(im->image_type->id)==id,"Incorrect stock image "+std::to_string(id));
        im->flags &= ~image_t::flag_hidden;im->sprite->flags &= ~sprite_t::flag_hidden;
        // Use the stock frame. Some initializers hide their child until a later animation opcode.
        // The coverage matrix exercises their visible state inside the full draw loop.
        classic();auto base=region(frame(),im->sprite->position,128,128);const auto before=world();
        load(single(singles,id),1);const auto hf=region(frame(),im->sprite->position,128,128);
        const auto n=diff(base,hf);require(n>0,"No changed pixels for image "+std::to_string(id));
        require(before==world(),"Graphics changed world for image "+std::to_string(id));
        if(id==36 || id==893){
            g_ui->st.game->eud_enabled95=true;
            const auto custom=region(frame(),im->sprite->position,128,128);
            classic();require(custom==region(frame(),im->sprite->position,128,128),"EUD fallback replaced stock art");
            g_ui->st.game->eud_enabled95=false;
        }
        std::cout<<"IMAGE "<<id<<' '<<n<<' '<<im->modifier<<' '<<im->frame_index<<"\n";
    }
    for(unsigned id:{361u,508u}){
        auto saved=pristine;restore_game(saved);g_ui->fog_player=-1;center(pos);
        auto* im=fixture(id,pos);require(im && im->modifier==9,"Missing stock remapped effect");
        im->flags &= ~image_t::flag_hidden;im->frame_index=std::min<size_t>(2,im->grp->frames.size()-1);
        classic();const auto base=region(frame(),pos,64,64);
        im->flags|=image_t::flag_hidden;
        require(diff(base,region(frame(),pos,64,64))>0,"Original remapped effect is not visible in the fixture");
        im->flags &= ~image_t::flag_hidden;
        load(all,29);require(region(frame(),pos,64,64)==base,"Remapped effect changed when the pack was enabled");
    }
    std::cout<<"PASS original Lockdown and Venom palette-remap effects with full pack enabled\n";
    // The real Broodling death iscript creates its remnant without test injection.
    auto saved=pristine;restore_game(saved);g_ui->fog_player=-1;center(pos);
    auto* brood=spawn(UnitTypes::Zerg_Broodling,pos);g_ui->kill_unit(brood);
    image_t* remnant=nullptr;
    for(int f=0;f<140 && !remnant;++f){
        native_step();for(auto& row:g_ui->st.sprites_on_tile_line)for(auto* s:ptr(row))
            if(std::abs(s->position.x-pos.x)<48 && std::abs(s->position.y-pos.y)<48)if(auto* im=find_image(s,7))remnant=im;
    }
    require(remnant,"Real Broodling death did not create remnants");classic();auto sd=region(frame(),pos,48,48);
    load(single(singles,7),1);require(diff(sd,region(frame(),pos,48,48))>0,"Real death remnants did not use their imported frame");
    std::cout<<"PASS native Broodling death produced and displayed imported remnants\n";
    auto clean=pristine;restore_game(clean);g_ui->fog_player=-1;center(pos);
    auto* carrier=spawn(UnitTypes::Zerg_Scourge,pos);auto* im=g_ui->create_image(g_ui->get_image_type(ImageTypes(1)),carrier->sprite,{},g_ui->image_order_top);
    require(im && im!=carrier->sprite->main_image,"Unable to create foreground shadow fixture");
    // Place an actual modifier-10 stock shadow over its HD body to measure a single blend.
    // Hiding only this new layer provides every underlying RGBA sample independently.
    im->offset={0,0};im->frame_index=0;
    load(all,29);const auto oldflags=im->flags;
    const auto center_pos=g_ui->get_image_center_map_position(im)-g_ui->screen_pos;
    std::ofstream(captures/"shadow.txt")<<center_pos.x<<' '<<center_pos.y<<' '<<im->frame_index<<'\n';
    im->flags=oldflags|image_t::flag_hidden;ppm(captures/"shadow-under.ppm",frame(),W*2,H*2);
    for(bool flip:{false,true}){
        im->flags=oldflags&~(image_t::flag_hidden|image_t::flag_horizontally_flipped);
        if(flip)im->flags|=image_t::flag_horizontally_flipped;
        ppm(captures/(flip?"shadow-mirror.ppm":"shadow-front.ppm"),frame(),W*2,H*2);
    }
    const auto original=world();const auto hs=save_game();for(int i=0;i<24;++i){native_step();if(i%4==0)frame();}
    const auto after=world();auto copy=hs;restore_game(copy);require(world()==original,"World mismatch after expanded save restore");
    classic();for(int i=0;i<24;++i){native_step();if(i%4==0)frame();}
    require(world()==after,"Expanded rendering changed the continued simulation");
    auto guest_fixture=pristine;restore_game(guest_fixture);g_ui->fog_player=0;center(pos);
    auto* building=spawn(UnitTypes::Zerg_Infested_Command_Center,pos);
    const auto building_id=g_ui->get_unit_id(building);const auto site=building->position;
    fog(false,true);building->sprite->visibility_flags=0;load(single(singles,63),1);
    net_active=true;net_remaining=0;
    auto host_save=net_save();g_ui->my_player=2;net_restore(host_save);net_active=false;
    building=g_ui->get_unit(building_id);require(building && g_ui->fog_player==2,"Guest restore did not switch viewer");
    center(site);const auto guest=region(frame(),site,100,90);
    require(!g_ui->fog_snapshots.at(building->sprite->index).observed.type,"Guest restore fabricated an HD sighting");
    classic();require(region(frame(),site,100,90)==guest,"Guest saw host-only HD observations");
    close_game();
    std::cout<<"PASS multiplayer guest restore does not inherit hidden HD observations\n";
    std::cout<<"PASS expanded pack simulation and 24-step save continuation\n";
}
int main(int argc,char** argv){
    try{
        std::cout.setf(std::ios::unitbuf);require(argc==9,"Usage: expanded STARDAT BROODAT PATCH STARCRAFT MAP ANIM_DIR SINGLE_ASSETS OUTPUT");
        for(int i=0;i<3;++i)open_archive(i,argv[i+1]);
        campaigns(argv[4],argv[7],argv[8]);skirmish(argv[5],argv[6],argv[7],argv[8]);
        std::cout<<"PASS all "<<ids.size()<<" stock images changed actual framebuffer samples\n";
        return 0;
    }catch(const std::exception& error){std::cerr<<"FAIL "<<error.what()<<'\n';return 1;}
}
