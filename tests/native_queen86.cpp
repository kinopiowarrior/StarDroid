// Queen research and summoned-unit lifetime through the production simulation.
// Supply the user's original core MPQs; no copyrighted assets are bundled.
#include "../native/runtime.cpp"
#include <iostream>
#include <sstream>
using namespace bwgame;
using U=UnitTypes; using T=TechTypes;
static int checks=0;
static void check(bool ok,const char* message){++checks;if(!ok)throw std::runtime_error(message);}
static void step(int frames){while(frames--)native_step();}
static void select(unit_t* u){g_ui->current_selection_clear();g_ui->current_selection_add(u);g_ui->on_selection(false);g_ui->refresh_card();}
static bool button(int action,T tech,bool enabledOnly=true){for(const auto& c:g_ui->card)if(c.act==action&&c.tech==tech&&(!enabledOnly||c.enabled))return true;return false;}
static unit_t* make(U type,int owner,xy around){
    auto* ut=g_ui->get_unit_type(type);
    for(int r=0;r<20;r++)for(int y=-r;y<=r;y++)for(int x=-r;x<=r;x++){
        xy at=around+xy(x*32,y*32);if(!g_ui->unit_type_can_fit_at(ut,at))continue;
        bool free=true;for(auto* u:g_ui->find_units(g_ui->square_at(at,48)))if(!g_ui->unit_dying(u))free=false;
        if(free)if(auto* u=g_ui->create_initial_unit(ut,at,owner))return u;
    }
    throw std::runtime_error("No room for test unit");
}
static std::vector<int> details(unit_t* u){select(u);std::string s=g_ui->unit_details();std::istringstream in(s.substr(0,s.find('\n')));std::vector<int> f;while(std::getline(in,s,'\t'))f.push_back(std::stoi(s));check(f.size()>=5,"Missing HUD fields");return f;}
static void setup(){
    close_game();open_archive(3,"app/src/main/assets/native/weave.scx");
    int races[]={0,1,2,0,0,0,0,0},slots[]={1,0,0,0,0,0,0,0};
    configured_init(640,480,0,races,slots,0,true,false,false);
    g_ui->competitive=false;g_ui->fog_player=-1;g_ui->rep_on=false;
    g_ui->st.current_minerals[0]=g_ui->st.current_gas[0]=10000;
}
static xy base(){for(auto* u:ptr(g_ui->st.player_units[0]))if(g_ui->unit_is(u,U::Zerg_Hatchery))return u->position;throw std::runtime_error("Missing base");}
static void research(){
    setup();auto* q=make(U::Zerg_Queen,0,base()+xy(200,100));auto* nest=make(U::Zerg_Queens_Nest,0,base()+xy(300,200));
    for(T t:{T::Parasite,T::Spawn_Broodlings,T::Ensnare})g_ui->st.tech_researched[0][t]=false;
    check(g_ui->get_tech_type(T::Parasite)->energy_cost==75,"Parasite energy cost");
    check(g_ui->get_tech_type(T::Spawn_Broodlings)->energy_cost==150,"Broodlings energy cost");
    check(g_ui->get_tech_type(T::Ensnare)->energy_cost==75,"Ensnare energy cost");
    select(q);check(button(g_ui->C_SPELL,T::Parasite),"Innate Parasite unavailable");check(button(g_ui->C_SPELL,T::Infestation),"Innate Infestation unavailable");
    check(!button(g_ui->C_SPELL,T::Spawn_Broodlings)&&!button(g_ui->C_SPELL,T::Ensnare),"Unresearched Queen spell enabled");
    select(nest);check(!button(g_ui->C_RESEARCH,T::Parasite,false),"Parasite offered for research");
    check(button(g_ui->C_RESEARCH,T::Spawn_Broodlings)&&button(g_ui->C_RESEARCH,T::Ensnare),"Queen research missing");
    action_functions af(g_ui->st,g_ui->current_action_state);af.action_select(0,nest);
    auto minerals=g_ui->st.current_minerals[0],gas=g_ui->st.current_gas[0];
    check(!af.action_research(0,g_ui->get_tech_type(T::Parasite)),"Raw command can research Parasite");
    check(g_ui->st.current_minerals[0]==minerals&&g_ui->st.current_gas[0]==gas,"Invalid research spent resources");
    auto* brood=g_ui->get_tech_type(T::Spawn_Broodlings);
    check(af.action_research(0,brood),"Cannot start paid Broodlings research");
    check(g_ui->st.current_minerals[0]==minerals-brood->mineral_cost&&g_ui->st.current_gas[0]==gas-brood->gas_cost,"Research charged wrong resources");
    select(q);check(!button(g_ui->C_SPELL,T::Spawn_Broodlings),"Spell enabled during research");
    step(brood->research_time+32);
    check(g_ui->player_has_researched(0,T::Spawn_Broodlings),"Research did not finish");
    select(q);check(button(g_ui->C_SPELL,T::Spawn_Broodlings),"Learned spell unavailable");
    select(nest);check(!button(g_ui->C_RESEARCH,T::Spawn_Broodlings,false),"Completed research still offered");
    // Scenario/UMS availability and pre-researched spells remain authoritative.
    auto& game=const_cast<game_state&>(g_ui->game_st);
    game.tech_available[0][T::Parasite]=false;select(q);check(!button(g_ui->C_SPELL,T::Parasite,false),"Disabled map tech visible");game.tech_available[0][T::Parasite]=true;
    g_ui->st.tech_researched[0][T::Ensnare]=true;select(q);check(button(g_ui->C_SPELL,T::Ensnare),"Pre-researched Ensnare unavailable");
    std::cout<<"PASS Queen innate spells, research cards, raw action rejection, paid research, map permissions and energy costs\n";
}
static void lifetime(){
    setup();auto* q=make(U::Zerg_Queen,0,base()+xy(180,80));auto* victim=make(U::Terran_Goliath,1,base()+xy(300,100));
    g_ui->spawn_broodlings(victim,q);std::vector<unit_id> ids;
    for(auto* u:ptr(g_ui->st.player_units[0]))if(g_ui->unit_is(u,U::Zerg_Broodling)){
        check(u->remove_timer==1800,"Broodling did not start with classic 1800 frames");
        auto f=details(u);check(f[3]==180&&f[4]==180,"Broodling HUD is not 180/180");ids.push_back(g_ui->get_unit_id(u));
    }
    check(ids.size()==2,"Spell did not create two Broodlings");
    for(int elapsed=1;elapsed<=1800;elapsed++){
        native_step();
        for(auto id:ids){auto* u=g_ui->get_unit(id);
            if(elapsed==1800){check(!u||g_ui->unit_dying(u),"Broodling survived its original lifespan");continue;}
            check(u&&!g_ui->unit_dying(u),"Broodling expired early");check(u->remove_timer==1800-elapsed,"Lifetime is not one tick per simulation frame");
            check(g_ui->unit_status_energy(u)==(1800-elapsed)/10,"Lifetime display has wrong scale or rounding");
            check(g_ui->unit_status_energy_percent(u)==(1800-elapsed)*100/1800,"World lifetime bar lost precision");
        }
        if(elapsed==900){auto bytes=save_game();restore_game(bytes);check(g_ui->get_unit(ids[0])->remove_timer==900,"Save/load changed lifetime");}
    }
    for(U type:{U::Protoss_Zealot,U::Protoss_High_Templar}){
        auto* original=make(type,0,base()+xy(180,200));auto* h=g_ui->create_hallucination(original,0);check(h!=nullptr,"Cannot create hallucination");
        auto f=details(h);check(f[3]==135&&f[4]==135,"Hallucination HUD is not 135/135");auto id=g_ui->get_unit_id(h);
        for(int i=0;i<1350;i++){check(h->remove_timer==1350-i,"Hallucination timer mismatch");check(g_ui->unit_status_energy(h)==(1350-i)/10,"Hallucination lifetime display");native_step();}
        check(!g_ui->get_unit(id)||g_ui->unit_dying(g_ui->get_unit(id)),"Hallucination survived expiry");
    }
    auto* caster=make(U::Protoss_High_Templar,0,base()+xy(200,250));caster->energy=fp8::integer(75);auto f=details(caster);check(f[3]==75&&f[4]==200,"Ordinary caster energy regressed");
    std::cout<<"PASS 1800-frame Broodling expiry, 180-point HUD, smooth bar, mid-life save/load; 1350-frame hallucinations and real caster energy\n";
}
int main(int argc,char** argv){try{
    std::cout.setf(std::ios::unitbuf);check(argc==2,"Usage: native-queen86 CORE_MPQ_DIRECTORY");std::string p=argv[1];
    open_archive(0,p+"/STARDAT.MPQ");open_archive(1,p+"/BROODAT.MPQ");open_archive(2,p+"/patch_rt.mpq");
    research();lifetime();close_game();std::cout<<"PASS "<<checks<<" Queen/lifetime checks\n";return 0;
}catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<"\n";return 1;}}
