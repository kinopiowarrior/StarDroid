// Long economy runs use caller-owned archives and normal production costs.
#define OPENBW_REVISION56_EMBEDDED
#include "native_revision56.cpp"
#undef main
#include <chrono>
int main(int argc,char** argv) {try {
 require(argc>=5,"CORE_DIR MAP RACE FRAMES");
 std::string data=argv[1];int race=std::atoi(argv[3]),frames=std::atoi(argv[4]);
 open_archive(0,data+"/STARDAT.MPQ");open_archive(1,data+"/BROODAT.MPQ");open_archive(2,data+"/patch_rt.mpq");open_archive(3,argv[2]);
 setup56(race,1,1,true,true,1);g_ui->competitive=false;g_ui->rep_on=false;
 std::set<int> seen;auto start=std::chrono::steady_clock::now();
 for(int f=0;f<frames;f++) {
  steps56(1);require(g_campaign->ai_error.empty(),g_campaign->ai_error.c_str());
  // Keep the passive opponent alive so this measures the economy after several waves.
  for(auto* u:ptr(g_ui->st.player_units[0]))if(!g_ui->unit_dying(u))g_ui->set_unit_hp(u,u->unit_type->hitpoints);
  for(auto* u:ptr(g_ui->st.player_units[1]))if(g_ui->u_completed(u)&&!g_ui->unit_dying(u))seen.insert(int(u->unit_type->id));
  if(f%6000==0)std::cout<<"MACRO "<<race<<" frame="<<f<<" "<<g_campaign->ai_status()<<std::flush;
 }
 int upgrades=0;for(auto n:g_ui->st.upgrade_levels[1])upgrades+=n;
 int bases=race==0?g_campaign->ai_zerg_bases(1):int(g_campaign->ai_depots(1).size());
 std::cout<<"RESULT race="<<race<<" bases="<<bases<<" upgrades="<<upgrades<<" waves="<<g_campaign->towns[1].attacks<<" seconds="<<std::chrono::duration<double>(std::chrono::steady_clock::now()-start).count()<<" types=";
 for(int t:seen)std::cout<<t<<",";std::cout<<std::endl;
 require(bases>=2,"AI did not expand");require(upgrades>=5,"AI upgrades stalled");require(g_campaign->towns[1].attacks>=2,"AI attack waves stalled");
 require(seen.count(race==0?39:race==1?9:84),"AI missing advanced unit/detector");
 if(race==1)require(seen.count(124)&&seen.count(125),"Terran did not construct turret and bunker");
 if(race==2)require(seen.count(162),"Protoss did not construct Photon Cannons");
 openbw_render(openbw_frame()*42.);
 auto state=save_game();steps56(96);auto future=save_game();restore_game(state);
 auto restored=save_game();if(restored!=state){std::ofstream a("build-native/macro85-before.sav",std::ios::binary),b("build-native/macro85-restored.sav",std::ios::binary);a.write((char*)state.data(),state.size());b.write((char*)restored.data(),restored.size());}
 require(restored==state,"Macro save roundtrip differs");steps56(96);require(save_game()==future,"Macro saved continuation diverged");
 std::cout<<"PASS medium AI: paid economy, expansion, upgrades, advanced units, repeated attacks, static defenses and deterministic save continuation\n";close_game();return 0;
 }catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<"\n";return 1;}}
