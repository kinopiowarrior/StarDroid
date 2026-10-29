// Actual archive/mission regressions. No game data is distributed with this test.
#define main retained_multiplayer_main77
#include "native_multiplayer.cpp"
#undef main
#include <sstream>
using U = bwgame::UnitTypes;
static unit_t* make77(U type,int owner,xy p) {
 auto* u=g_ui->create_initial_unit(g_ui->get_unit_type(type),p,owner);
 require(u,"Could not create regression unit");return u;
}
static unit_t* room77(U type,int owner,xy around) {
 auto* ut=g_ui->get_unit_type(type);
 for(int r=0;r<20;r++)for(int y=-r;y<=r;y++)for(int x=-r;x<=r;x++) {
  xy p=around+xy(x*32,y*32);if(!g_ui->unit_type_can_fit_at(ut,p))continue;
  bool free=true;for(auto* u:g_ui->find_units(g_ui->square_at(p,64)))if(!g_ui->unit_dying(u))free=false;
  if(free){auto* u=g_ui->create_initial_unit(ut,p,owner);if(u)return u;}
 }
 throw std::runtime_error("No free placement for regression unit");
}
static void setup77(const char* map) {
 close_game();open_archive(3,map);
 int races[8]={0,1},slots[8]={1,0};configured_init(640,480,0,races,slots,0,true,true,false);
 g_ui->competitive=false;if(g_campaign)g_campaign->threads.clear();
}
static void morph78(const char* map) {
 for(bool single:{false,true})for(int presses:{1,3})for(bool between:{false,true}) {
  setup77(map);g_ui->single_larva_morph=single;g_ui->st.current_minerals[0]=5000;g_ui->st.current_gas[0]=5000;
  auto* parent=room77(U::Zerg_Hatchery,0,{1100,1100});
  g_ui->current_selection_clear();std::vector<unit_id> ids;
  for(int i=0;i<3;i++){auto* u=room77(U::Zerg_Larva,0,{1100+i*25,1200});u->connected_unit=parent;g_ui->current_selection_add(u);ids.push_back(g_ui->get_unit_id(u));}
  g_ui->refresh_card();char key=0;for(auto& c:g_ui->card)if(c.act==g_ui->C_MORPH&&c.ut==U::Zerg_Drone)key=c.key;
  require(key,"No larva morph button");for(int i=0;i<presses;i++) {
   if(!single&&between&&i>0)break;
   require(g_ui->run_command(key),"Morph command not handled");
   if(between){native_step();g_ui->refresh_card();}
  }
  require(g_ui->current_selection.size()==3,"Single morph changed visible selection");native_step();
  int eggs=0;for(auto id:ids){auto* u=g_ui->get_unit(id);if(g_ui->unit_is(u,U::Zerg_Egg)||!u->build_queue.empty())eggs++;}
  require(eggs==(single?presses:3),"Wrong number of larvae morphed");require(g_ui->pending_larva_morphs.empty(),"Applied larva reservation leaked");
 }
 std::cout<<"PASS one larva per press, three rapid or spaced presses, default group morph and unchanged selection\n";
}
static void original78(const char* archive,int frames,bool inspect) {
 close_game();open_archive(3,archive);openbw_init_campaign(640,480,104);openbw_set_external_cursor(1);
 for(int p=0;p<8;p++){
  std::map<int,int> counts;for(auto* u:ptr(g_ui->st.player_units[p]))counts[int(u->unit_type->id)]++;
  std::cout<<"PLAYER "<<p<<" race="<<int(g_ui->st.players[p].race)<<" controller="<<int(g_ui->st.players[p].controller)<<" units";for(auto pair:counts)std::cout<<" "<<pair.first<<":"<<pair.second;std::cout<<"\n";
 }
 int loaded=0,delivered=0;std::set<unsigned> seen;
 for(int i=0;i<frames;i++) {
  native_step();
  if(i%24==0)for(int p=0;p<8;p++)if(p!=g_ui->my_player)for(auto* u:ptr(g_ui->st.player_units[p])) {
   auto id=g_ui->get_unit_id(u).raw_value;
   if(g_ui->u_loaded(u)){if(seen.insert(id).second)loaded++;}
   else if(seen.erase(id)){delivered++;}
  }
  if(i%2400==0) {
   for(int p=0;p<8;p++)if(g_campaign->towns[p].active) {
    auto& a=g_campaign->towns[p];auto target=g_campaign->ai_target(p,a.center);int ground=0,blocked=0,ships=0;
    for(auto id:a.attackers)if(auto* u=g_ui->get_unit(id))if(!g_ui->u_flying(u)){ground++;if(!g_ui->is_reachable(u,target))blocked++;}
    for(auto* u:ptr(g_ui->st.player_units[p]))if(u->unit_type->space_provided&&!g_ui->ut_building(u)) {ships++;std::cout<<" SHIP "<<int(u->unit_type->id)<<" o="<<int(u->order_type->id)<<" pos="<<u->position.x<<","<<u->position.y<<" space="<<g_ui->unit_space_left(u)<<";";}
    std::cout<<" AI "<<p<<" target="<<target.x<<","<<target.y<<" group="<<g_ui->get_region_at(target)->group_index<<" ground="<<ground<<" blocked="<<blocked<<" ships="<<ships<<" lifts="<<a.lifts.size()<<"\n";
   }
  }
  if(i%2400==0)std::cout<<"MISSION "<<i<<" loaded="<<loaded<<" delivered="<<delivered<<" error="<<g_campaign->ai_error<<" trace="<<g_campaign->ai_trace<<std::endl;
 }
 std::cout<<"RESULT loaded="<<loaded<<" delivered="<<delivered<<" error="<<g_campaign->ai_error<<"\n";
 if(!inspect){require(loaded>0&&delivered>0,"Original Zerg 4 did not load and deliver attackers");require(g_campaign->ai_error.empty(),"Mission AI script error");}
}
static void air78(const char* archive) {
 for(bool melee:{false,true})for(auto type:{U::Terran_Dropship,U::Protoss_Shuttle,U::Zerg_Overlord}) {
  close_game();open_archive(3,archive);openbw_init_campaign(640,480,104);openbw_set_external_cursor(1);
  auto& game=const_cast<game_state&>(g_ui->game_st);
  for(auto& list:g_ui->st.running_triggers)list.clear();game.triggers.clear();g_campaign->threads.clear();g_campaign->spawned.clear();g_campaign->towns={};g_campaign->melee_ai=melee;
  unit_t* enemy=nullptr;xy home;for(auto* u:ptr(g_ui->st.player_units[3]))if(g_ui->ut_resource_depot(u)){enemy=u;break;}
  for(auto* u:ptr(g_ui->st.player_units[1]))if(g_ui->unit_is(u,U::Terran_Marine)){home=u->position;break;}
  require(enemy,"No mission island base");xy enemyPos=enemy->position;g_ui->u_set_status_flag(enemy,unit_t::status_flag_invincible);
  for(int p=0;p<12;p++){a_vector<unit_t*> units;for(auto* u:ptr(g_ui->st.player_units[p]))if(u!=enemy&&!g_ui->ut_turret(u)&&!g_ui->unit_is_mineral_field(u)&&!g_ui->unit_is(u,U::Resource_Vespene_Geyser))units.push_back(u);for(auto* u:units)if(!g_ui->unit_dying(u))g_ui->kill_unit(u);}
  // Let old collision boxes disappear without the melee elimination poll.
  for(int i=0;i<72;i++)g_ui->state_functions::next_frame();g_ui->outcome=0;
  g_ui->st.players[1].race=type==U::Zerg_Overlord?race_t::zerg:type==U::Protoss_Shuttle?race_t::protoss:race_t::terran;
  g_ui->st.current_minerals[1]=g_ui->st.current_gas[1]=0;
  auto* ship=make77(type,1,home);auto shipId=g_ui->get_unit_id(ship);
  if(type==U::Zerg_Overlord){require(!g_campaign->ai_transport(ship),"Overlord carried units without Ventral Sacs");g_ui->st.upgrade_levels[1][UpgradeTypes::Ventral_Sacs]=1;}
  auto& town=g_campaign->towns[1];town.active=true;town.center=home;
  std::vector<unit_id> cargo;for(int i=0;i<9;i++){auto* u=room77(U::Terran_Marine,1,home+xy(i*20,60));require(!g_ui->is_reachable(u,enemyPos),"Fixture is not across terrain");cargo.push_back(g_ui->get_unit_id(u));town.attackers.push_back(cargo.back());}
  if(!melee&&type==U::Protoss_Shuttle) {
   town.active=false;town.attackers.clear();
   for(auto id:cargo)g_ui->issue_order(g_ui->get_unit(id),false,g_ui->get_order_type(Orders::AttackMove),{enemyPos,nullptr});
  }
  int loaded=0,delivered=0;std::set<unsigned> riding,landed;bool snapshotChecked=false,bulkDeparture=false;
  for(int frame=0;frame<16000&&delivered<9;frame++) {
   native_step();
   if(frame%24)continue;
   auto* carrier=g_ui->get_unit(shipId);require(carrier&&!g_ui->unit_dying(carrier),"Fixture lost transport");require(g_ui->unit_space_left(carrier)<=8,"Overfilled transport");
   for(auto id:cargo)if(auto* u=g_ui->get_unit(id)) {
    if(g_ui->u_loaded(u)){if(riding.insert(id.raw_value).second)loaded++;}
    else if(riding.count(id.raw_value)&&g_ui->is_reachable(u,enemyPos)&&landed.insert(id.raw_value).second)delivered++;
   }
   if(!bulkDeparture)for(const auto& job:g_campaign->towns[1].lifts)if(job.transport==shipId&&job.phase==1){
    require(g_ui->unit_space_left(carrier)<=2,"First assault departed with less than 75 percent cargo");bulkDeparture=true;
   }
   if(loaded>0&&!snapshotChecked) {
    auto state=save_game();restore_game(state);require(save_game()==state,"Saved airlift did not round trip");
    for(int i=0;i<120;i++)native_step();auto future=save_game();restore_game(state);for(int i=0;i<120;i++)native_step();require(save_game()==future,"Airlift diverged after restoration");
    snapshotChecked=true;
   }
  }
  std::cout<<"AIRLIFT type="<<int(type)<<" melee="<<melee<<" loaded="<<loaded<<" delivered="<<delivered<<std::endl;
  require(delivered==9&&snapshotChecked&&bulkDeparture,"Transport failed to deliver two waves of cargo");
  if(melee&&type==U::Terran_Dropship) {
   auto* worker=room77(U::Terran_SCV,1,home);auto workerId=g_ui->get_unit_id(worker);auto* cc=g_ui->get_unit_type(U::Terran_Command_Center);
   xy site;bool found=false;
   for(int y=2;y<int(g_ui->game_st.map_tile_height)-3&&!found;y++)for(int x=2;x<int(g_ui->game_st.map_tile_width)-3;x++) {
    xy pos=xy(x*32,y*32)+cc->placement_size/2;
    if(g_ui->is_reachable(pos,enemyPos)&&!g_ui->is_reachable(worker,pos)&&g_ui->can_place_building(nullptr,1,cc,pos,false,true,true)) {site=pos;found=true;break;}
   }
   require(found,"No island construction site");
   campaign_ai::airlift lift;lift.transport=shipId;lift.passengers.push_back(workerId);lift.target=site;lift.started=g_campaign->ai_clock;lift.build_type=int(U::Terran_Command_Center);
   require(g_campaign->ai_landing(worker,site,home,lift.landing),"No builder landing");
   g_campaign->towns[1].lifts.clear();g_campaign->towns[1].lifts.push_back(lift);g_campaign->towns[1].active=false;
   g_ui->st.current_minerals[1]=1000;
   bool started=false;
   for(int frame=0;frame<6000&&!started;frame++) {
    native_step();for(auto* u:ptr(g_ui->st.player_units[1]))if(g_ui->unit_is(u,U::Terran_Command_Center)&&g_ui->xy_length(u->position-site)<32)started=true;
   }
   require(started,"Ferried worker did not start construction");
   std::cout<<"PASS island builder ferry resumes Command Center construction after unloading\n";
  }
  // A destroyed empty carrier is reclaimed rather than retaining a dead job.
  g_campaign->towns[1].lifts.push_back({});g_campaign->towns[1].lifts.back().transport=shipId;
  g_ui->kill_unit(g_ui->get_unit(shipId));a_vector<campaign_ai::request> req;g_campaign->ai_airlifts(1,req);
  require(g_campaign->towns[1].lifts.empty(),"Destroyed transport retained a job");
 }
 std::cout<<"PASS all three transports in campaign and melee: terrain crossing, capacity, repeat trips, Ventral Sacs gate, deterministic save/restore and destroyed-carrier cleanup\n";
}

static void morphExtras78(const char* map) {
 setup77(map);g_ui->single_larva_morph=true;
 auto* parent=room77(U::Zerg_Hatchery,0,{1100,1100});room77(U::Zerg_Spawning_Pool,0,{1600,1400});
 g_ui->current_selection_clear();std::vector<unit_id> ids;
 for(int i=0;i<3;i++){auto* u=room77(U::Zerg_Larva,0,{1100+i*25,1200});u->connected_unit=parent;ids.push_back(g_ui->get_unit_id(u));g_ui->current_selection_add(u);}
 g_ui->st.current_minerals[0]=50;g_ui->st.current_gas[0]=0;g_ui->st.supply_available[0][0]=fp1::integer(200);g_ui->refresh_card();char key=0;
 for(auto& c:g_ui->card)if(c.act==g_ui->C_MORPH&&c.ut==U::Zerg_Zergling)key=c.key;
 require(key&&g_ui->run_command(key),"No Zergling morph");auto state=save_game();restore_game(state);require(g_ui->pending_larva_morphs.size()==1,"Save lost queued input reservation");
 native_step();g_ui->refresh_card();g_ui->run_command(key);require(g_ui->outgoing.empty(),"Unaffordable morph emitted input");
 for(int i=0;i<500;i++)native_step();int zerglings=0;for(auto* u:ptr(g_ui->st.player_units[0]))if(g_ui->unit_is(u,U::Zerg_Zergling))zerglings++;
 require(zerglings==2,"One larva no longer produces a pair of Zerglings");
 g_ui->st.current_minerals[0]=5000;g_ui->st.current_gas[0]=5000;
 room77(U::Zerg_Lair,0,{1800,1500});g_ui->st.tech_researched[0][TechTypes::Lurker_Aspect]=true;
 g_ui->current_selection_clear();ids.clear();for(int i=0;i<2;i++){auto* u=room77(U::Zerg_Hydralisk,0,{1200+i*32,1300});ids.push_back(g_ui->get_unit_id(u));g_ui->current_selection_add(u);}
 g_ui->refresh_card();key=0;for(auto& c:g_ui->card)if(c.act==g_ui->C_MORPH&&c.ut==U::Zerg_Lurker)key=c.key;
 require(key&&g_ui->run_command(key),"No Lurker morph");native_step();for(auto id:ids){auto* u=g_ui->get_unit(id);require(g_ui->unit_is(u,U::Zerg_Lurker_Egg)||!u->build_queue.empty(),"Single-larva preference changed Hydralisk group morphs");}
 std::cout<<"PASS queued-input save restoration, insufficient minerals, normal two-Zergling output and unchanged Hydralisk group morphs\n";
}
static void sweep78(const char* starcraft,const char* broodwar) {
 int missions=0,owners=0;
 for(const auto& entry:web_campaigns) {
  close_game();open_archive(3,entry.expansion?broodwar:starcraft);openbw_init_campaign(640,480,entry.code);openbw_set_external_cursor(1);
  for(int owner=0;owner<8;owner++)if(owner!=g_ui->my_player&&g_ui->st.players[owner].controller==player_t::controller_computer_game) {
   a_vector<campaign_ai::request> req;auto upgrades=g_campaign->towns[owner].upgrades;
   g_campaign->ai_transport_requests(owner,16,req);
   for(auto item:req)require(g_ui->game_st.unit_type_allowed[owner][U(item.type)],"Transport prerequisites unlocked a mission-forbidden unit");
   if(g_ui->player_max_upgrade_level(owner,UpgradeTypes::Ventral_Sacs)==0&&!g_ui->player_has_upgrade(owner,UpgradeTypes::Ventral_Sacs))
    require(g_campaign->towns[owner].upgrades.size()==upgrades.size(),"Mission-forbidden Ventral Sacs requested");
   g_campaign->towns[owner].upgrades=upgrades;owners++;
  }
  for(int frame=0;frame<48;frame++)native_step();require(g_campaign->ai_error.empty(),"Campaign failed transport integration smoke check");missions++;
 }
 std::cout<<"PASS "<<missions<<" campaign maps / "<<owners<<" computer owners: load/tick integration and mission unit/upgrade restrictions\n";
}

#ifdef OPENBW_REVISION78_EMBEDDED
#define main retained_revision78_main85
#endif
int main(int argc,char**argv) {try{
 require(argc>=4,"DATA MAP STARCRAFT [frames]");std::string d=argv[1];open_archive(0,d+"/STARDAT.MPQ");open_archive(1,d+"/BROODAT.MPQ");open_archive(2,d+"/patch_rt.mpq");
 if(argc>5&&std::string(argv[4])=="sweep"){sweep78(argv[3],argv[5]);return 0;}
 if(argc>4&&std::string(argv[4])=="air"){air78(argv[3]);return 0;}
 morph78(argv[2]);morphExtras78(argv[2]);if(argc==4)air78(argv[3]);original78(argv[3],argc>4?atoi(argv[4]):18000,argc>4);close_game();return 0;
}catch(const std::exception&e){std::cerr<<"FAIL "<<e.what()<<"\n";return 1;}}
