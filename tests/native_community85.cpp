// Community regressions using caller-owned original archives; no game assets bundled.
#define main controls80_main
#include "native_controls80.cpp"
#undef main
#include <sstream>
static std::vector<int> fields85(const char* text){std::vector<int> out;std::istringstream in(std::string(text).substr(0,std::string(text).find('\n')));std::string v;while(std::getline(in,v,'\t'))out.push_back(std::stoi(v));return out;}
static bool has85(int action){for(auto& c:g_ui->card)if(c.act==action&&c.enabled)return true;return false;}
static void transport85(const char* archive){
 setup(archive);int p=g_ui->my_player;xy at=units(U::Zerg_Hatchery,p).front()->position;
 for(U type:{U::Terran_Dropship,U::Protoss_Shuttle,U::Zerg_Overlord}){
  g_ui->st.upgrade_levels[p][UpgradeTypes::Ventral_Sacs]=1;
  auto* empty=room(type,p,at+xy(160,100));auto* full=room(type,p,at+xy(250,100));auto* cargo=room(U::Terran_Marine,p,at+xy(250,150));
  g_ui->unit_load_target(full,cargo);
  for(bool reverse:{false,true}){
   select(reverse?full:empty);g_ui->current_selection_add(reverse?empty:full);g_ui->refresh_card();
   require(has85(g_ui->C_UNLOADALL),"Mixed empty/loaded transport selection has no Unload");require(has85(g_ui->C_LOAD),"Mixed selection has no Load");
  }
  g_ui->unit_unload(cargo);select(empty);g_ui->current_selection_add(full);g_ui->refresh_card();require(!has85(g_ui->C_UNLOADALL),"All-empty selection has Unload");
 }
 std::cout<<"PASS mixed transport command cards for all three races and both selection orders\n";
}
static void status85(const char* archive){
 setup(archive);int p=g_ui->my_player;xy at=units(U::Zerg_Hatchery,p).front()->position;
 for(U type:{U::Protoss_Zealot,U::Protoss_High_Templar}){
  auto* original=room(type,p,at+xy(160,160));auto* h=g_ui->create_hallucination(original,p);require(h,"Hallucination creation failed");
  for(int remaining:{1350,675,27,1}){
   h->remove_timer=remaining;select(h);auto f=fields85(g_ui->unit_details());require(f[3]==remaining/10&&f[4]==135,"Hallucination HUD lost classic lifetime units");
   require(g_ui->unit_status_energy_percent(h)==remaining*100/1350,"Hallucination bar is not proportional");
  }
  auto id=g_ui->get_unit_id(h);step(10);require(!g_ui->get_unit(id)||g_ui->unit_dying(g_ui->get_unit(id)),"Expired hallucination survived");
 }
 auto* caster=room(U::Protoss_High_Templar,p,at+xy(220,220));caster->energy=fp8::integer(75);select(caster);auto f=fields85(g_ui->unit_details());require(f[3]==75&&f[4]==g_ui->unit_max_energy(caster).integer_part(),"Real caster energy regressed");
 std::cout<<"PASS hallucination life bars: full/half/near expiry, caster precedence, expiry; real caster unchanged\n";
}
static void kills85(const char* archive){
 setup(archive);int p=g_ui->my_player;xy at=units(U::Zerg_Hatchery,p).front()->position;
 auto* killer=room(U::Terran_Marine,p,at+xy(250,100));auto* victim=room(U::Zerg_Zergling,1,at+xy(350,100));
 g_ui->unit_deal_damage(victim,fp8::integer(5000),killer,p);require(killer->kill_count==1,"Weapon kill count not credited");g_ui->unit_deal_damage(victim,fp8::integer(5000),killer,p);require(killer->kill_count==1,"Kill credited twice");select(killer);require(fields85(g_ui->unit_details())[0]==1,"Kill count absent from HUD");
 auto* tank=room(U::Terran_Siege_Tank_Tank_Mode,p,at+xy(350,200));victim=room(U::Zerg_Zergling,1,at+xy(450,100));g_ui->unit_deal_damage(victim,fp8::integer(5000),tank->subunit,p);require(tank->kill_count==1,"Turret kill not credited to tank");
 auto* queen=room(U::Zerg_Queen,p,at+xy(250,250));victim=room(U::Terran_Marine,1,at+xy(450,250));g_ui->spawn_broodlings(victim,queen);require(queen->kill_count==1,"Broodlings spell not credited");
 killer->kill_count=255;victim=room(U::Zerg_Zergling,1,at+xy(450,400));g_ui->unit_deal_damage(victim,fp8::integer(5000),killer,p);require(killer->kill_count==255,"Kill counter overflowed");
 std::cout<<"PASS lethal hit credited once, tank turret, Broodlings spell, HUD and 255 cap\n";
}
static void supply85(const char* archive){
 setup(archive);int p=g_ui->my_player;auto* base=units(U::Zerg_Hatchery,p).front();auto* cc=room(U::Zerg_Infested_Command_Center,p,base->position+xy(400,400));auto* type=g_ui->get_unit_type(U::Zerg_Infested_Terran);
 auto& game=const_cast<game_state&>(g_ui->game_st);game.unit_type_allowed[p][U::Zerg_Infested_Terran]=true;g_ui->st.supply_available[p][0]=fp1::integer(200);
 auto before=g_ui->st.supply_used[p][0];require(type->supply_required==fp1::integer(1),"Original Infested Terran cost is not 1");
 select(cc);g_ui->sync_selection();g_ui->cmd_type(31,U::Zerg_Infested_Terran);step(1);require(g_ui->st.supply_used[p][0]==before+fp1::integer(1),"Infested starts at wrong supply");
 auto id=g_ui->get_unit_id(cc->current_build_unit);for(int i=0;i<1000&&!g_ui->u_completed(g_ui->get_unit(id));i++)native_step();auto* built=g_ui->get_unit(id);require(built&&g_ui->u_completed(built),"Infested production failed");require(g_ui->st.supply_used[p][0]==before+fp1::integer(1),"Infested completion changed supply");
 g_ui->kill_unit(built);step(2);require(g_ui->st.supply_used[p][0]==before,"Infested death released wrong supply");
 select(cc);g_ui->sync_selection();g_ui->cmd_type(31,U::Zerg_Infested_Terran);step(3);require(cc->current_build_unit,"Cancel fixture did not start");g_ui->cancel_build_queue(cc);step(2);require(g_ui->st.supply_used[p][0]==before,"Canceled Infested Terran leaked supply");
 for(int other=0;other<12;other++)g_ui->st.alliances[p][other]=g_ui->st.alliances[other][p]=1;
 // Standing mission attack orders must not destroy this isolated producer.
 g_ui->u_set_status_flag(cc,unit_t::status_flag_invincible);
 auto ccid=g_ui->get_unit_id(cc);
 select(cc);g_ui->sync_selection();for(int i=0;i<3;i++)g_ui->cmd_type(31,U::Zerg_Infested_Terran);step(1);
 require(g_ui->st.supply_used[p][0]==before+fp1::integer(1),"Waiting Infested queue incorrectly reserves extra supply");
 auto snapshot=save_game();restore_game(snapshot);cc=g_ui->get_unit(ccid);
 std::vector<unit_t*> army;
 for(int f=0;f<3000;f++){
  native_step();army=units(U::Zerg_Infested_Terran,p);
  if(army.size()==3&&std::all_of(army.begin(),army.end(),[](unit_t* u){return g_ui->u_completed(u);}))break;
 }
require(army.size()==3,"Infested queue did not finish three units");require(g_ui->st.supply_used[p][0]==before+fp1::integer(3),"Infested queue/saved continuation double counts supply");
 auto* ship=room(U::Terran_Dropship,p,cc->position+xy(200,200));g_ui->unit_load_target(ship,army[0]);g_ui->unit_load_target(ship,army[1]);g_ui->kill_unit(ship);step(3);
 require(g_ui->st.supply_used[p][0]==before+fp1::integer(1),"Two Infested transport deaths released wrong supply");
 g_ui->st.alliances[p][1]=g_ui->st.alliances[1][p]=0;
 auto* suicide=army[2];auto sid=g_ui->get_unit_id(suicide);auto* enemy=room(U::Terran_Marine,1,suicide->position+xy(80,0));enemy->hp=fp8::integer(10000);enemy->lockdown_timer=1000;
 g_ui->issue_order(suicide,false,suicide->unit_type->attack_unit,{enemy->position,enemy});
 for(int i=0;i<600&&g_ui->get_unit(sid)&&!g_ui->unit_dying(g_ui->get_unit(sid));i++)native_step();
 require(!g_ui->get_unit(sid)||g_ui->unit_dying(g_ui->get_unit(sid)),"Infested suicide did not detonate");step(3);require(g_ui->st.supply_used[p][0]==before,"Infested suicide released wrong supply");
 std::cout<<"PASS Infested Terran real command stream: one supply each, training/completion/cancel, queued production+save restore, transport deaths and suicide\n";
}
static void burrow85(const char* archive,bool strict){
 setup(archive);int p=g_ui->my_player;xy at=units(U::Zerg_Hatchery,p).front()->position;
 for(U type:{U::Zerg_Zergling,U::Zerg_Hydralisk,U::Zerg_Lurker}){
  auto* u=room(type,1,at+xy(500,300));g_ui->apply_map_unit_flags(u,2,2);
  if(strict)require(g_ui->u_burrowed(u)&&g_ui->u_requires_detector(u),"Map/UPRP spawn was not already burrowed");
  int previous=-1;for(int f=0;f<180;f++){native_step();int now=int(u->order_type->id);if(now!=previous){std::cout<<"BURROW "<<int(type)<<" f="<<f<<" order="<<now<<" burrowed="<<g_ui->u_burrowed(u)<<"\n";previous=now;}}
  if(strict)require(g_ui->u_burrowed(u),"Initial burrow did not remain stable");
  g_ui->unburrow_unit(u);g_ui->set_queued_order(u,false,g_ui->get_order_type(Orders::Move),u->position+xy(96,0));
  for(int f=0;f<180;f++){native_step();int now=int(u->order_type->id);if(now!=previous){std::cout<<"UNBURROW "<<int(type)<<" f="<<f<<" order="<<now<<" burrowed="<<g_ui->u_burrowed(u)<<"\n";previous=now;}}
  if(strict)require(!g_ui->u_burrowed(u)&&u->order_type->id!=Orders::Burrowing,"Scripted unburrow loops");
 }
}
static void traps85(const char* archive,bool strict){
 for(U type:{U::Special_Floor_Gun_Trap,U::Special_Floor_Missile_Trap,U::Special_Wall_Missile_Trap,U::Special_Wall_Flame_Trap,U::Special_Right_Wall_Missile_Trap,U::Special_Right_Wall_Flame_Trap}){
  setup(archive);int p=g_ui->my_player;xy at=units(U::Zerg_Hatchery,p).front()->position;
  auto* trap=room(type,1,at+xy(550,300));step(45);auto* w=g_ui->unit_ground_weapon(trap);std::cout<<"TRAP_DAT "<<int(type)<<" range="<<(w?g_ui->weapon_max_range(trap,w):0)<<" acquire="<<g_ui->unit_target_acquisition_range(trap)<<" turn="<<g_ui->u_can_turn(trap)<<" angle="<<(w?w->attack_angle.raw_value:0)<<"\n";xy approach=int(type)>=212?xy(-48,0):xy(48,0);
  auto* target=g_ui->create_initial_unit(g_ui->get_unit_type(U::Terran_Marine),trap->position+approach,p);require(target,"Trap target missing");target->hp=fp8::integer(10000);target->lockdown_timer=1000;
  int previous=-1,reveals=0,hides=0,lastimage=-1;for(int f=0;f<300;f++){native_step();int now=int(trap->order_type->id);if(int(type)==210&&f<80&&lastimage!=trap->sprite->main_image->frame_index){lastimage=trap->sprite->main_image->frame_index;std::cout<<"IMAGE f="<<f<<" frame="<<lastimage<<" anim="<<trap->sprite->main_image->iscript_state.animation<<" hp="<<target->hp.integer_part()<<" target="<<(trap->order_target.unit?int(trap->order_target.unit->unit_type->id):-1)<<" distance="<<g_ui->units_distance(trap,target)<<"\n";}if(now!=previous){std::cout<<"TRAP "<<int(type)<<" f="<<f<<" order="<<now<<" image="<<trap->sprite->main_image->frame_index<<"\n";if(trap->order_type->id==Orders::RevealTrap)++reveals;if(trap->order_type->id==Orders::HideTrap)++hides;previous=now;}}
  if(strict){require(reveals==1&&hides==0,"Trap repeatedly hides/reveals with stationary target");require(target->hp<fp8::integer(10000),"Trap failed to attack");}
 }
}

static void trap_arcs85(const char* archive){
 for(U type:{U::Special_Wall_Missile_Trap,U::Special_Wall_Flame_Trap,U::Special_Right_Wall_Missile_Trap,U::Special_Right_Wall_Flame_Trap}){
  setup(archive);int p=g_ui->my_player;xy at=units(U::Zerg_Hatchery,p).front()->position;
  auto* trap=room(type,1,at+xy(550,300));step(45);xy front=int(type)>=212?xy(-48,0):xy(48,0);
  auto* target=g_ui->create_initial_unit(g_ui->get_unit_type(U::Terran_Marine),trap->position+front,p);require(target,"Missing out-of-arc target");target->hp=fp8::integer(10000);target->lockdown_timer=1000;
  step(50);require(trap->order_type->id==Orders::TowerAttack,"Trap did not first reveal");openbw_debug_warp(g_ui->get_unit_id(target).raw_value,trap->position.x-front.x,trap->position.y-front.y);
  int hides=0,reveals=0,previous=-1;
  for(int f=0;f<360;f++){native_step();int now=int(trap->order_type->id);if(now!=previous){hides+=trap->order_type->id==Orders::HideTrap;reveals+=trap->order_type->id==Orders::RevealTrap;previous=now;}}
  std::cout<<"TRAP_ARC type="<<int(type)<<" reveals="<<reveals<<" hides="<<hides<<"\n";require(reveals==0&&hides==0,"Wall trap oscillated for target outside its firing arc");
  g_ui->kill_unit(target);step(90);require(trap->order_type->id==Orders::HiddenGun,"Trap did not close after threat left");
  auto* next=g_ui->create_initial_unit(g_ui->get_unit_type(U::Terran_Marine),trap->position+front,p);require(next,"Missing in-arc target");next->hp=fp8::integer(10000);next->lockdown_timer=1000;step(300);require(next->hp<fp8::integer(10000),"Trap did not fire after reopening");
 }
 std::cout<<"PASS four fixed wall traps: out-of-arc target stays revealed, threat loss closes, later front target takes damage\n";
}
static void campaign_burrow85(const char* original,const char* expansion){
 for(int mission:{206,301,302}){
  close_game();open_archive(3,mission>=300?expansion:original);openbw_init_campaign(640,480,mission);
  int initial=0,properties=0;trigger::action prop{};
  for(int p=0;p<12;p++)for(auto* u:ptr(g_ui->st.player_units[p]))if(g_ui->u_burrowed(u)){initial++;require(g_ui->u_requires_detector(u),"Campaign initial burrow visible without detection");}
  for(const auto& t:g_ui->game_st.triggers)for(const auto& a:t.actions)if(a.type==11&&(a.flags&8)&&a.group2_n>=1&&a.group2_n<=64&&g_campaign->properties.size()>=size_t(a.group2_n)*20){
   const auto* b=&g_campaign->properties[(a.group2_n-1)*20];if((b[0]&2)&&(b[14]&2)){properties++;prop=a;}
  }
  for(auto& list:g_ui->st.running_triggers)list.clear();const_cast<game_state&>(g_ui->game_st).triggers.clear();g_campaign->threads.clear();g_campaign->towns={};
  auto own=g_ui->st.player_units[g_ui->my_player].begin();require(own!=g_ui->st.player_units[g_ui->my_player].end(),"Campaign fixture no local units");xy at=own->position;
  int owner=g_ui->my_player;auto* marker=room(U::Zerg_Hydralisk,owner,at+xy(250,200));xy spawn=marker->position;g_ui->kill_unit(marker);step(72);
  // Retain an actual map's burrow property when supplied; otherwise exercise
  // the same documented UPRP bit on that campaign's terrain and unit tables.
  if(!properties){g_campaign->properties.resize(20);g_campaign->properties[0]=2;g_campaign->properties[14]=2;prop.group2_n=1;}
  g_ui->st.locations.push_back({g_ui->square_at(spawn,24),0});int from=int(g_ui->st.locations.size());
  g_ui->st.locations.push_back({g_ui->square_at(spawn+xy(128,0),16),0});int to=int(g_ui->st.locations.size());
  prop.type=11;prop.flags=8;prop.extra_n=int(U::Zerg_Hydralisk);prop.num_n=1;prop.group_n=owner;prop.location=from;
  g_campaign->campaign_create(owner,prop);unit_t* created=nullptr;
  for(auto* u:g_ui->find_units(g_ui->st.locations[from-1].area))if(g_ui->unit_is(u,U::Zerg_Hydralisk)&&!g_ui->unit_dying(u))created=u;
  require(created&&g_ui->u_burrowed(created),"Create-with-properties did not immediately spawn burrowed unit");auto id=g_ui->get_unit_id(created);
  trigger::action order{};order.type=46;order.group_n=owner;order.extra_n=int(U::Zerg_Hydralisk);order.location=from;order.group2_n=to;order.num_n=0;
  state_functions::execute_trigger_struct ets{};running_trigger rt{};running_trigger::action ra{};
  g_ui->execute_trigger_action(ets,owner,rt,ra,order);
  for(int f=0;f<240;f++)native_step();created=g_ui->get_unit(id);
  require(created&&!g_ui->unit_dying(created)&&!g_ui->u_burrowed(created)&&created->order_type->id!=Orders::Burrowing,"Same-pass scripted Order lost spawn or reburrowed it");
  std::cout<<"PASS campaign "<<mission<<": initialBurrowed="<<initial<<" burrowPropertyActions="<<properties<<"; Create+Order same pass finishes unburrow without loop\n";
 }
}

static void projectile_edges85(const char* archive){
 setup(archive);int p=g_ui->my_player;xy at=units(U::Zerg_Hatchery,p).front()->position;
 auto* source=room(U::Protoss_Dragoon,p,at+xy(200,100));auto* target=room(U::Terran_Marine,1,source->position+xy(100,0));source->order_target.unit=target;
 for(xy off:{xy(-2,100),xy(100,-2),xy(g_ui->game_st.map_width+2,100),xy(100,g_ui->game_st.map_height+2)}){
  auto* bullet=g_ui->create_bullet(g_ui->unit_ground_weapon(source),source,source->position,p,source->heading);require(bullet&&bullet->sprite,"Projectile fixture failed");
  auto* sprite=bullet->sprite;auto original=sprite->position;sprite->position=off;g_ui->update_bullets();
  require(!sprite->visibility_flags,"Out-of-map projectile remained visible");sprite->position=original;
 }
 std::cout<<"PASS animated projectile fog lookup at all four map edges\n";
}

static void reset_audio85(){for(size_t i=0;i<g_ui->sound_channels.size();i++){native_sound::stop(i);g_ui->sound_channels[i].playing=false;}std::fill(g_ui->last_played_sound.begin(),g_ui->last_played_sound.end(),decltype(g_ui->last_played_sound)::value_type{});}
static void presentation85(const char* archive){
 for(U type:{U::Terran_Marine,U::Protoss_Zealot,U::Zerg_Hydralisk}){
  setup(archive);int p=g_ui->my_player;g_ui->fog_player=-1;auto* base=units(U::Zerg_Hatchery,p).front();select(base);g_ui->poll_events();reset_audio85();
  auto* made=room(type,p,base->position+xy(300,100));g_ui->poll_events();
  require(value_query(30,0)==int(type),"New unit ready speech did not take portrait");require(g_ui->primary_selected()==base,"Ready portrait changed selection");
  auto serial=g_ui->ready_portrait_serial;g_ui->poll_events();require(g_ui->ready_portrait_serial==serial,"Ready portrait restarted every poll");
  std::vector<int16_t> pcm(48000*20);naudio::mix(pcm.data(),48000*10,48000);require(value_query(30,0)==-1,"Portrait did not return after speech");
  reset_audio85();auto* next=room(type,p,base->position+xy(350,100));g_ui->poll_events();require(value_query(30,0)==int(type),"Second ready portrait missing");select(base);require(value_query(30,0)==-1,"New selection did not dismiss ready portrait");
  (void)made;(void)next;
 }
 setup(archive);int p=g_ui->my_player;auto* base=units(U::Zerg_Hatchery,p).front();xy rescueAt=base->position;g_ui->st.players[1].controller=player_t::controller_rescue_passive;
 auto* hero=room(U::Hero_Edmund_Duke_Tank_Mode,1,base->position+xy(300,150));int body=hero->sprite->owner,turret=hero->subunit->sprite->owner;auto id=g_ui->get_unit_id(hero);g_ui->give_unit_to(hero,p);
 require(hero->owner==p&&hero->sprite->owner==body&&hero->subunit->sprite->owner==turret,"Rescue changed original body/turret color");auto saved=save_game();restore_game(saved);hero=g_ui->get_unit(id);require(hero->sprite->owner==body&&hero->subunit->sprite->owner==turret,"Rescue color lost on load");
 g_ui->st.players[1].controller=player_t::controller_computer_game;auto* ordinary=room(U::Terran_Marine,1,rescueAt+xy(250,250));g_ui->give_unit_to(ordinary,p);require(ordinary->sprite->owner==p,"Ordinary Give failed to change color");
 std::cout<<"PASS ready portraits for all races: real speech lifetime, stable selection, override dismissal; rescue body/turret colors and saved restoration\n";
}
static void mouse85(const char* archive){
 setup(archive);int p=g_ui->my_player;g_ui->fog_player=-1;auto* worker=units(U::Zerg_Drone,p).front();auto* mineral=units(U::Resource_Mineral_Field,11).front();
 auto click=[&](unit_t* target){select(worker);g_ui->center_on(target->position);xy screen=target->position-g_ui->screen_pos;command(0,screen.x,screen.y,0,0);openbw_render(1000);require(openbw_cursor()==3,"Native cursor failed to recognize target hover");command(33,1,3,1,0);command(33,0,3,1,0);openbw_render(1042);native_step();};
 click(mineral);require(worker->order_target.unit==mineral||worker->worker.gather_target==mineral,"Physical right click failed to gather minerals");
 auto* gas=room(U::Zerg_Extractor,p,worker->position+xy(320,100));gas->building.resource.resource_count=1000;click(gas);require(worker->order_target.unit==gas||worker->worker.gather_target==gas,"Physical right click failed to gather gas");
 auto* enemy=room(U::Terran_Marine,1,worker->position+xy(160,160));click(enemy);require(worker->order_target.unit==enemy,"Physical right click failed to attack enemy");
 std::cout<<"PASS native physical mouse context orders: mineral harvest, gas harvest, enemy attack\n";
}
int main(int argc,char** argv){std::cout.setf(std::ios::unitbuf);try{
 require(argc>=4,"Usage: native-community85 CORE_DIR STARCRAFT_MPQ core|probe|burrow|traps");std::string d=argv[1],mode=argv[3];open_archive(0,d+"/STARDAT.MPQ");open_archive(1,d+"/BROODAT.MPQ");open_archive(2,d+"/patch_rt.mpq");
 if(mode=="core"){transport85(argv[2]);status85(argv[2]);kills85(argv[2]);supply85(argv[2]);}
 else if(mode=="probe"){burrow85(argv[2],false);traps85(argv[2],false);}
 else if(mode=="campaign-burrow"){require(argc>=5,"Brood War archive required");campaign_burrow85(argv[2],argv[4]);}
 else if(mode=="projectile-edges")projectile_edges85(argv[2]);
 else if(mode=="trap-arcs")trap_arcs85(argv[2]);
 else if(mode=="presentation")presentation85(argv[2]);else if(mode=="mouse")mouse85(argv[2]);
 else if(mode=="burrow")burrow85(argv[2],true);else if(mode=="traps")traps85(argv[2],true);
 else throw std::runtime_error("Unknown test mode");close_game();return 0;
 }catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<"\n";return 1;}}
