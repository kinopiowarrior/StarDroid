// Terrain, production and saved-continuation checks with caller-owned data.
#define OPENBW_REVISION78_EMBEDDED
#include "native_revision78.cpp"
#undef main
#undef OPENBW_REVISION78_EMBEDDED
static void skirmish85(const char* map,int race){
 close_game();open_archive(3,map);int races[8]={1,race},slots[8]={1,2};configured_init(640,480,0,races,slots,1,true,true,false);
 g_ui->competitive=false;g_campaign->threads.clear();g_campaign->spawned.clear();
 g_ui->st.current_minerals[1]=g_ui->st.current_gas[1]=5000;g_campaign->ai_clock=10000;
 auto& town=g_campaign->towns[1];town.center=g_campaign->ai_depots(1).front()->position;town.active=true;
}
static void expansion85(const char* map){
 for(int race:{1,2,0}){
  skirmish85(map,race);auto bases=g_campaign->ai_depots(1);require(bases.size()==1,"Expansion fixture needs one base");auto* main=bases.front();
  unit_t* nearest=nullptr;int distance=0x7fffffff;
  for(auto* r:ptr(g_ui->st.player_units[11]))if(g_ui->unit_is_mineral_field(r)&&r->building.resource.resource_count>0){
   if(g_ui->xy_length(r->position-main->position)<512)continue;
   bool enemy=false;for(auto* b:ptr(g_ui->st.player_units[0]))if(g_ui->ut_resource_depot(b)&&g_ui->xy_length(b->position-r->position)<640)enemy=true;
   if(enemy)continue;int d=g_ui->long_path_distance(main->position,r->position);if(d<distance){distance=d;nearest=r;}
  }
  require(nearest,"No natural mineral line");require(g_campaign->ai_expand(1),"AI did not reserve an expansion");
  unit_t* builder=nullptr;for(auto* u:ptr(g_ui->st.player_units[1]))if(g_ui->ut_worker(u)&&(!u->build_queue.empty()||u->order_type->id==Orders::Move))builder=u;
  require(builder,"AI found no expansion builder");xy site=builder->order_target.pos;
  std::cout<<"EXPAND_DEBUG base="<<main->position.x<<","<<main->position.y<<" nearest="<<nearest->position.x<<","<<nearest->position.y<<" ground="<<distance<<" site="<<site.x<<","<<site.y<<" path="<<g_ui->long_path_distance(site,nearest->position)<<"\n";
  require(g_ui->long_path_distance(site,nearest->position)<=640,"Expansion ignored closest natural");
  require(g_ui->is_reachable(builder,site),"Ground builder sent to disconnected site");
  int resources=0;for(auto* r:ptr(g_ui->st.player_units[11]))if(g_ui->unit_is_mineral_field(r)&&g_ui->xy_length(r->position-site)<400){require(g_ui->long_path_distance(site,r->position)<=640,"Depot is across a cliff from its minerals");++resources;}
  require(resources>=4,"Depot is too far from its mineral line");
  auto planned=site;g_ui->cancel_build_queue(builder);g_ui->issue_order(builder,false,g_ui->get_order_type(Orders::Stop),{});g_campaign->ai_clock=12500;
  require(g_campaign->ai_expand(1),"Expansion retry failed");require(builder->order_target.pos==planned,"Clock rotated expansion to a distant resource cluster");
  std::cout<<"PASS natural expansion race="<<race<<" site="<<site.x<<","<<site.y<<" fields="<<resources<<"\n";
 }
}
static void repairs85(const char* map){
 skirmish85(map,1);auto* base=g_campaign->ai_depots(1).front();g_ui->set_unit_hp(base,fp8::integer(400));auto before=base->hp;
 g_campaign->ai_repair_structures(1);int assigned=0;for(auto* u:ptr(g_ui->st.player_units[1]))if(u->order_type->id==Orders::Repair&&u->order_target.unit==base)++assigned;
 require(assigned==2,"Critical structure did not receive two SCVs");
 g_campaign->ai_repair_structures(1);int again=0;for(auto* u:ptr(g_ui->st.player_units[1]))if(u->order_type->id==Orders::Repair&&u->order_target.unit==base)++again;require(again==2,"Repair recruited every worker");
 for(int i=0;i<400;i++)g_ui->state_functions::next_frame();require(base->hp>before,"Assigned SCVs did not repair structure");
 g_ui->set_unit_hp(base,base->unit_type->hitpoints);for(int i=0;i<48;i++)g_ui->state_functions::next_frame();
 for(auto* u:ptr(g_ui->st.player_units[1]))require(u->order_type->id!=Orders::Repair,"Repair order stuck after completion");
 std::cout<<"PASS Terran structure repair: two-SCV cap, actual HP recovery and finished-job release\n";
}
static void defenders85(const char* map){
 skirmish85(map,1);auto* base=g_campaign->ai_depots(1).front();unit_t* worker=nullptr;for(auto* u:ptr(g_ui->st.player_units[1]))if(g_ui->ut_worker(u)){worker=u;break;}require(worker,"No worker fixture");
 auto* enemy=room77(U::Protoss_Zealot,0,worker->position+xy(80,0));enemy->sprite->visibility_flags=0xff;
 auto& town=g_campaign->towns[1];std::vector<unit_id> defenders;
 for(int i=0;i<6;i++){auto* marine=room77(U::Terran_Marine,1,base->position+xy(i*40,160));g_ui->set_unit_order(marine,g_ui->get_order_type(Orders::Guard));auto id=g_ui->get_unit_id(marine);defenders.push_back(id);town.attackers.push_back(id);}
 g_campaign->on_scenario_attack(worker,enemy);g_campaign->ai_defend(1);int sent=0;
 for(auto id:defenders){auto* u=g_ui->get_unit(id);if(u->order_type->id==Orders::AttackMove&&u->order_target.pos==enemy->position){sent++;require(!g_campaign->ai_attacking(town,u),"Defender still reserved for offensive wave");}}
 require(sent>=4,"Nearby idle wave reserves did not defend workers promptly");
 std::cout<<"PASS worker defense recruited "<<sent<<" nearby idle reserves immediately and released offensive reservations\n";
}
static void island85(const char* archive){
 close_game();open_archive(3,archive);openbw_init_campaign(640,480,104);auto& game=const_cast<game_state&>(g_ui->game_st);
 for(auto& list:g_ui->st.running_triggers)list.clear();game.triggers.clear();g_campaign->threads.clear();g_campaign->towns={};
 xy home{};for(auto* u:ptr(g_ui->st.player_units[1]))if(g_ui->unit_is(u,U::Terran_Marine)){home=u->position;break;}require(home!=xy(),"Missing island fixture home");
 for(int p=0;p<12;p++){a_vector<unit_t*> remove;for(auto* u:ptr(g_ui->st.player_units[p]))if(!g_ui->ut_turret(u)&&!g_ui->ut_resource(u))remove.push_back(u);for(auto* u:remove)if(!g_ui->unit_dying(u))g_ui->kill_unit(u);}
 for(int i=0;i<72;i++)g_ui->state_functions::next_frame();
 g_ui->st.players[1].race=race_t::terran;g_ui->st.players[1].controller=player_t::controller_computer_game;g_campaign->melee_ai=true;g_campaign->ai_clock=15000;
 auto* base=room77(U::Terran_Command_Center,1,home);auto* worker=room77(U::Terran_SCV,1,home+xy(160,0));auto wid=g_ui->get_unit_id(worker);
 auto& town=g_campaign->towns[1];town.center=base->position;town.active=false;
 for(auto* resource:ptr(g_ui->st.player_units[11]))if(g_ui->unit_is_mineral_field(resource)&&g_ui->is_reachable(worker,resource->position))resource->building.resource.resource_count=0;
 g_ui->st.current_minerals[1]=1000;g_ui->st.current_gas[1]=1000;
 require(!g_campaign->ai_expand(1),"Disconnected expansion issued a ground construction order");
 require(worker->build_queue.empty(),"SCV tried building at the near cliff");bool requested=false;for(auto r:town.builds)if(r.type==int(U::Terran_Dropship))requested=true;require(requested,"Island expansion did not request transport");
 auto* ship=room77(U::Terran_Dropship,1,worker->position);require(g_campaign->ai_expand(1)&&town.lifts.size()==1,"Island builder was not assigned to available transport");
 xy site=town.lifts.front().target;require(!g_ui->is_reachable(worker,site),"Island expansion placed on wrong shore");int fields=0;for(auto* r:ptr(g_ui->st.player_units[11]))if(g_ui->unit_is_mineral_field(r)&&g_ui->long_path_distance(site,r->position)<640)++fields;require(fields>=4,"Island depot cannot reach minerals");
 bool started=false;for(int f=0;f<6000&&!started;f++){
  g_ui->state_functions::next_frame();++g_campaign->ai_clock;
  if(f%24==0){a_vector<campaign_ai::request> req;g_campaign->ai_airlifts(1,req);}
  for(auto* u:ptr(g_ui->st.player_units[1]))if(g_ui->unit_is(u,U::Terran_Command_Center)&&g_ui->xy_length(u->position-site)<32)started=true;
 }
 if(!started){worker=g_ui->get_unit(wid);std::cout<<"ISLAND_DEBUG site="<<site.x<<","<<site.y<<" worker="<<worker->position.x<<","<<worker->position.y<<" order="<<int(worker->order_type->id)<<" queue="<<worker->build_queue.size()<<" lifts="<<town.lifts.size()<<" reachable="<<g_ui->is_reachable(worker,site)<<" place="<<g_ui->can_place_building(worker,1,g_ui->get_unit_type(U::Terran_Command_Center),site,false,false)<<"\n";}
 require(started,"Ferried SCV did not begin depot on resource island");require(g_ui->get_unit(wid),"Island worker disappeared");(void)ship;
 std::cout<<"PASS island expansion requests transport, rejects near cliff and starts depot after ferrying SCV\n";
}
int main(int argc,char** argv){std::cout.setf(std::ios::unitbuf);try{
 require(argc>=5,"CORE_DIR MAP STARCRAFT_MPQ direct|air|island");std::string d=argv[1];open_archive(0,d+"/STARDAT.MPQ");open_archive(1,d+"/BROODAT.MPQ");open_archive(2,d+"/patch_rt.mpq");std::string mode=argv[4];
 if(mode=="direct"){expansion85(argv[2]);repairs85(argv[2]);defenders85(argv[2]);}
 else if(mode=="sweep"){require(argc>=6,"Brood War archive required");sweep78(argv[3],argv[5]);}
 else if(mode=="air")air78(argv[3]);else if(mode=="island")island85(argv[3]);else throw std::runtime_error("Unknown mode");close_game();return 0;
 }catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<"\n";return 1;}}
