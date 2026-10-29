// Seeking regression built on the shared recorder, validators and hash
// helpers. Every expected state comes from ordinary linear replay simulation.
#define main replay81_historic_main
#include "native_replay81.cpp"
#undef main
#include <chrono>
#include <limits>
#include <map>
#include <set>

struct ReplayReference82 {
 std::vector<uint8_t> bytes;
 std::vector<uint64_t> sync;
 std::map<int,uint64_t> worlds;
 std::set<int> targets;
 int frames=0;
};
static int seekCalls82=0,seekPumps82=0;static double longestPump82=0;
static size_t peakCacheBytes82=0,peakCheckpoints82=0;static bool renderEveryPump82=false;
static int checkpointEvictions82=0,checkpointRestores82=0;static std::set<int> previousCacheFrames82;
static void cache_bound82() {
 peakCacheBytes82=std::max(peakCacheBytes82,native_replay::cache_bytes());peakCheckpoints82=std::max(peakCheckpoints82,native_replay::checkpoints.size());
 std::set<int> cached;for(auto& checkpoint:native_replay::checkpoints)cached.insert(checkpoint.frame);
 if(cached.size()==native_replay::MAX_CHECKPOINTS&&previousCacheFrames82.size()==native_replay::MAX_CHECKPOINTS&&cached!=previousCacheFrames82)++checkpointEvictions82;
 previousCacheFrames82=std::move(cached);
 check(native_replay::checkpoints.size()<=native_replay::MAX_CHECKPOINTS,"Seek checkpoint count grew beyond its fixed budget");
 check(native_replay::cache_bytes()<=native_replay::MAX_CHECKPOINT_BYTES,"Seek checkpoint bytes grew beyond their fixed budget");
 check(!native_replay::checkpoints.empty()&&native_replay::checkpoints.front().frame==0,"Seek discarded its frame-zero checkpoint");
}
static void verify82(const ReplayReference82& ref,bool full=true) {
 int frame=g_ui->st.current_frame;
 check(frame>=0&&frame<=ref.frames,"Replay current frame is outside its timeline");
 if(sync_hash()!=ref.sync.at(frame)) {
  std::cerr<<"SEEK divergence frame="<<frame<<" expected="<<ref.sync.at(frame)<<" got="<<sync_hash()<<"\n";
  check(false,"Seek changed simulation, commands or AI relative to linear playback");
 }
 auto expected=ref.worlds.find(frame);
 if(full&&expected!=ref.worlds.end())check(world_hash()==expected->second,"Seek full world/AI snapshot differs from linear playback");
 check(g_ui->outgoing.empty()&&!g_ui->rep_on&&g_ui->spectator,"Seeking escaped read-only replay mode");
 check(replay_seek_audio_muted==native_replay::seeking,"Seek sound gate differs from pending-work state");
 if(g_campaign)check(g_campaign->ai_error.empty(),"Seek AI script failed");
 cache_bound82();
}
static void pump82(const ReplayReference82& ref) {
 int target=native_replay::seek_target,beforeFrame=g_ui->st.current_frame;
 int base=g_ui->st.current_frame<=target?g_ui->st.current_frame:0;
 for(auto& checkpoint:native_replay::checkpoints)if(checkpoint.frame<=target)base=std::max(base,checkpoint.frame);
 auto started=std::chrono::steady_clock::now();
 native_replay::pump_seek();++seekPumps82;
 double elapsed=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-started).count();
 longestPump82=std::max(longestPump82,elapsed);
 check(g_ui->st.current_frame<=base+native_replay::SEEK_BATCH_FRAMES,"Seek pump ran an unbounded simulation batch");
 if(beforeFrame>target||base>beforeFrame){check(g_ui->st.current_frame==base,"Seek did not restore its nearest usable checkpoint");++checkpointRestores82;}
 check(g_ui->st.current_frame<=target,"Seek overshot requested frame");
 if(renderEveryPump82){auto before=world_hash();openbw_render(g_ui->st.current_frame*42.0);check(world_hash()==before,"Rendering an intermediate seek world mutated simulation/AI");}
 verify82(ref,false);
}
static void finish82(const ReplayReference82& ref) {
 int guard=0;
 while(native_replay::seeking) {
  pump82(ref);
  check(++guard<=ref.frames+2,"Seek did not converge");
 }
 verify82(ref);
}
static void seek82(const ReplayReference82& ref,int requested) {
 bool paused=g_ui->paused;int target=std::max(0,std::min(ref.frames,requested));
 native_replay::seek(requested);++seekCalls82;
 check(native_replay::seek_target==target,"Seek failed to clamp its target frame");
 check(value_query(29,0)==target,"Seek target is missing from the native UI query");
 check(value_query(28,0)==int(native_replay::seeking),"Seek progress is missing from the native UI query");
 check(replay_seek_audio_muted==native_replay::seeking,"Queued seek did not mute stale game audio");
 finish82(ref);
 check(g_ui->st.current_frame==target,"Seek completed at the wrong frame");
 check(g_ui->paused==paused,"Seeking changed the user's pause state");
}
static void render82(const ReplayReference82& ref,bool capture) {
 auto before=world_hash();openbw_render(42000.0);
 check(world_hash()==before,"Post-seek render changed deterministic world state");
 check(g_ui->get_replay_slider_area()==rect{},"Nonfunctional desktop replay slider remains in native renderer");
 int width=openbw_framebuffer_width(),height=openbw_framebuffer_height();auto* pixels=openbw_framebuffer();
 check(pixels&&width==640&&height==480,"Seek did not retain the native framebuffer");
 size_t colored=0;for(int i=0;i<width*height;i++)if(pixels[4*i]||pixels[4*i+1]||pixels[4*i+2])++colored;
 check(colored>1000,"Post-seek renderer produced an empty frame");
 if(capture&&!capturePath.empty()) {
  std::ofstream out(capturePath,std::ios::binary);out<<"P6\n"<<width<<" "<<height<<"\n255\n";
  for(int i=0;i<width*height;i++)out.write(reinterpret_cast<const char*>(pixels)+4*i,3);
  check(out.good(),"Could not write seek regression framebuffer");
 }
 verify82(ref);
}
static ReplayReference82 fixture82(int race,int frames,bool expansion,bool teams,bool heroes,bool classicOnly) {
 int races[]={race,(race+1)%3,(race+2)%3,1,1,1,1,1};
 int controllers[]={1,classicOnly?0:2,teams?2:0,0,0,0,0,0},teamIds[]={1,2,1,1,1,1,1,1};
 nearby_defenders=true;
 configured_init(640,480,0,races,controllers,2,expansion,false,false,teams?teamIds:nullptr,teams,heroes);
 auto* worker=unit(workerTypes[race]);check(worker!=nullptr,"Seek fixture has no worker");
 auto workerId=g_ui->get_unit_id(worker);auto position=worker->position;
 for(int frame=0;frame<frames;frame++){
  if(frame<=144)inputs(frame,race,workerId,position);
  if(frame==900){auto* original=g_ui->get_unit(workerId);check(original&&!g_ui->unit_dying(original),"Late replay command worker absent");select(original);g_ui->cmds.queued(26,false);}
  // No reselect: restoring the1440-frame checkpoint must preserve the recorded
  // action selection plus its still-pending1500-frame action-stream cursor.
  if(frame==1500)g_ui->cmds.order(Orders::Move,position+xy(64,64),0,false);
  if(frame==1700)g_ui->cmds.queued(26,false);
  if(frame==2500){select(unit(producerTypes[race]));g_ui->cmds.type(race==0?35:31,workerTypes[race]);}
  native_step();
 }
 check(g_ui->st.current_frame==frames,"Seek fixture ended before its requested duration");
 auto recorded=world_hash();ReplayReference82 ref;ref.bytes=native_replay::save();ref.frames=frames;
 auto parsed=native_replay::parse(ref.bytes.data(),ref.bytes.size());
 check(parsed.frames==frames&&parsed.actions.size()>30,"Seek fixture lacks commands or valid duration");
 check(native_replay::ENGINE==87,"Unexpected simulation rules for the integrated campaign fixes");
 if(classicOnly)ref.bytes.resize(parsed.classicSize);
 ref.targets={0,1,63,64,65,95,96,97,143,144,145,511,512,899,900,901,1199,1200,1439,1440,1441,1499,1500,1501,1699,1700,1701,2199,2200,2499,2500,2501,frames/2,frames-1,frames};
 for(int frame=300;frame<frames;frame+=317)ref.targets.insert(frame);
 for(auto i=ref.targets.begin();i!=ref.targets.end();)if(*i>frames)i=ref.targets.erase(i);else ++i;
 native_replay::open(ref.bytes.data(),ref.bytes.size());ref.sync.reserve(frames+1);
 for(int frame=0;frame<=frames;frame++) {
  ref.sync.push_back(sync_hash());
  if(ref.targets.count(frame))ref.worlds.emplace(frame,world_hash());
  if(frame<frames)native_step();
 }
 check(world_hash()==recorded,"Linear replay fixture differs from the game that recorded it");
 check(native_replay::playing.rules.present!=classicOnly,"Replay footer fixture mode changed");
 native_replay::open(ref.bytes.data(),ref.bytes.size());
 return ref;
}
static void snapshot_cost82(const ReplayReference82& ref,const char* label) {
 auto before=world_hash();native_save::HostState host;
 auto started=std::chrono::steady_clock::now();auto bytes=native_save::save(*g_ui,g_campaign,host);
 double captureMs=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-started).count();
 started=std::chrono::steady_clock::now();native_save::restore(*g_ui,g_campaign,bytes);
 double restoreMs=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-started).count();
 check(world_hash()==before,"Direct snapshot measurement changed world/AI state");verify82(ref);
 int units=0;for(auto& list:g_ui->st.player_units)for(auto* unit:ptr(list)){(void)unit;++units;}
 std::cout<<"PASS checkpoint timing scenario="<<label<<" units="<<units<<" snapshotBytes="<<bytes.size()<<" captureMs="<<captureMs<<" restoreMs="<<restoreMs<<"\n";
}
static void fallback82(const ReplayReference82& ref) {
 seek82(ref,1500);command(6,1,0,0,0);command(31,1,0,0,0);command(3,32,16,0,0);command(8,1,0,0,0);command(19,1,0,0,0);command(21,96,0,0,0);command(26,1,0,0,0);
 auto camera=g_ui->screen_pos;int width=g_ui->screen_width,height=g_ui->screen_height;auto scale=g_ui->minimap_scale;
 native_replay::checkpoints.clear();native_replay::checkpoint_disabled=true;
 native_replay::seek(0);++seekCalls82;finish82(ref);
 check(g_ui->st.current_frame==0&&g_ui->paused,"Reload fallback lost frame or pause state");
 check(native_replay::viewpoint==1&&g_ui->fog_player==1&&g_ui->screen_pos==camera,"Reload fallback lost camera or viewpoint");
 check(g_ui->screen_width==width&&g_ui->screen_height==height&&g_ui->minimap_scale==scale&&g_ui->hud_bottom_inset==96,"Reload fallback lost viewport settings");
 check(g_ui->show_order_lines&&g_ui->host_controls_edge_scroll&&openbw_minimap_terrain(),"Reload fallback lost presentation preferences");
 check(!native_replay::checkpoint_disabled&&!native_replay::checkpoints.empty(),"Reload fallback did not reconstruct its checkpoint anchor");
 seek82(ref,145);verify82(ref);command(31,0,0,0,0);render82(ref,false);
 std::cout<<"PASS unavailable-checkpoint fallback: original replay reload preserves world, pause, camera, viewpoint and presentation settings\n";
}
static void exercise82(const ReplayReference82& ref,const char* label,bool capture) {
 verify82(ref);check(!native_replay::seeking,"Fresh replay retained a pending seek");
 // Paused scrubbing across command boundaries must still run reconstruction.
 command(6,1,0,0,0);command(31,1,0,0,0);command(3,32,16,0,0);
 auto screen=g_ui->screen_pos;
 for(int target:{ref.frames-1,64,145,0,96,ref.frames,1,144,143,95,97,63,65,511,512,900}) {
  seek82(ref,target);
  check(native_replay::viewpoint==1&&g_ui->fog_player==1,"Seek changed selected replay viewpoint");
  check(g_ui->screen_pos==screen,"Seek changed replay camera position");
  int pausedFrame=g_ui->st.current_frame;native_step();check(g_ui->st.current_frame==pausedFrame,"Paused playback advanced after seek");
 }
 seek82(ref,std::numeric_limits<int>::max());native_step();check(g_ui->st.current_frame==ref.frames,"Replay advanced beyond end after seek");
 seek82(ref,std::numeric_limits<int>::min());seek82(ref,0);seek82(ref,0);
 // Replace a long pending seek, then interrupt again in the reverse direction.
 native_replay::open(ref.bytes.data(),ref.bytes.size());command(6,1,0,0,0);
 native_replay::seek(ref.frames);++seekCalls82;
 check(native_replay::seeking,"Distant seek completed synchronously");
 int queuedFrame=g_ui->st.current_frame;command(6,0,0,0,0);native_step();check(g_ui->st.current_frame==queuedFrame,"Ordinary playback advanced while seek work was queued");command(6,1,0,0,0);
 pump82(ref);check(native_replay::seeking,"Distant seek monopolized one pump");
 native_replay::seek(900);++seekCalls82;finish82(ref);check(g_ui->st.current_frame==900,"A superseded seek target won over the newest request");
 native_replay::seek(ref.frames);++seekCalls82;pump82(ref);
 native_replay::seek(1);++seekCalls82;finish82(ref);check(g_ui->st.current_frame==1,"Backward seek replacement did not restart from a valid checkpoint");
 // Cancellation keeps the already reconstructed valid frame and playback state.
 native_replay::seek(ref.frames);++seekCalls82;pump82(ref);int cancelledFrame=g_ui->st.current_frame;
 native_replay::cancel_seek();check(!native_replay::seeking,"Seek cancellation left work queued");
 native_replay::pump_seek();check(g_ui->st.current_frame==cancelledFrame,"Cancelled seek still advanced");verify82(ref);
 command(6,0,0,0,0);native_step();check(g_ui->st.current_frame==cancelledFrame+1,"Playback did not resume from a cancelled seek");verify82(ref,false);
 // Forward and backward seeks with normal playback continuing through commands.
 for(int target:{0,63,95,143,1200,2199,ref.frames/2}) {
  seek82(ref,target);
  for(int i=0;i<37&&g_ui->st.current_frame<ref.frames;i++){native_step();verify82(ref,false);}
 }
 // Many repeated scrubs exercise checkpoint reuse/eviction without retaining a
 // snapshot for every target; every full serialized state remains identical.
 for(auto i=ref.targets.rbegin();i!=ref.targets.rend();++i)seek82(ref,*i);
 for(int target:ref.targets)seek82(ref,target);
 seek82(ref,1500);render82(ref,capture);snapshot_cost82(ref,label);fallback82(ref);
 readonly_controls();verify82(ref);
 seek82(ref,0);command(6,0,0,0,0);
 for(int frame=1;frame<=ref.frames;frame++){native_step();verify82(ref,ref.targets.count(frame));}
 seek82(ref,ref.frames);check(native_replay::save().empty(),"Seeking enabled recording during replay playback");
 rejected([]{save_game();},"Seeking allowed replay world to be saved as a live game");
 native_replay::seek(0);++seekCalls82;check(native_replay::seeking,"Restart precondition did not create pending seek");
 native_replay::open(ref.bytes.data(),ref.bytes.size());check(!native_replay::seeking&&g_ui->st.current_frame==0,"Reopening replay retained a pending seek");verify82(ref);
 ++scenarios;
 std::cout<<"PASS seek scenario="<<label<<" frames="<<ref.frames<<" targets="<<ref.targets.size()<<" footer="<<native_replay::playing.rules.present<<" cacheBytes="<<native_replay::cache_bytes()<<" checkpoints="<<native_replay::checkpoints.size()<<"\n";
}
static void long_pending82(const ReplayReference82& ref) {
 auto changed=native_replay::parse(ref.bytes.data(),ref.bytes.size());
 constexpr int maximum=24*60*60*24;
 for(int i=0;i<4;i++)changed.header[1+i]=uint8_t(maximum>>(i*8));
 changed.frames=maximum;auto bytes=classic(changed);
 native_replay::open(bytes.data(),bytes.size());
 native_replay::seek(maximum);++seekCalls82;
 check(native_replay::seeking&&g_ui->st.current_frame==0,"A 24-hour seek ran synchronously in the request");
 pump82(ref);
 check(native_replay::seeking&&g_ui->st.current_frame<=native_replay::SEEK_BATCH_FRAMES,"A 24-hour seek ignored its work bound");
 native_replay::cancel_seek();check(!native_replay::seeking,"Long seek could not be cancelled between slices");
 verify82(ref,false);
 native_replay::seek(0);++seekCalls82;finish82(ref);
 check(g_ui->st.current_frame==0,"Long seek could not be replaced with a backward seek");
 std::cout<<"PASS 24-hour replay: request only queues work, first pump bounded, cancel and restart responsive\n";
}
static void crowded_snapshot82() {
 // Synthetic performance fixture only: it does not supply expected seek states.
 int races[]={1,2,0,1,1,1,1,1},controllers[]={1,2,0,0,0,0,0,0};
 configured_init(640,480,0,races,controllers,2,true,false,false);
 for(int i=0;i<200;i++)check(g_ui->create_initial_unit(g_ui->get_unit_type(UnitTypes::Terran_Wraith),xy(256+(i%20)*64,256+(i/20)*64),i%2)!=nullptr,"Could not create crowded snapshot timing fixture");
 auto before=world_hash();native_save::HostState host;
 auto started=std::chrono::steady_clock::now();auto bytes=native_save::save(*g_ui,g_campaign,host);
 double captureMs=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-started).count();
 started=std::chrono::steady_clock::now();native_save::restore(*g_ui,g_campaign,bytes);
 double restoreMs=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-started).count();
 check(world_hash()==before,"Crowded snapshot restore changed world/AI state");
 int units=0;for(auto& list:g_ui->st.player_units)for(auto* unit:ptr(list)){(void)unit;++units;}
 check(units>=200,"Crowded snapshot fixture has fewer than200units");
 std::cout<<"PASS checkpoint stress synthetic=1 units="<<units<<" snapshotBytes="<<bytes.size()<<" captureMs="<<captureMs<<" restoreMs="<<restoreMs<<"\n";close_game();
}
int main(int argc,char** argv){try{
 std::cout.setf(std::ios::unitbuf);check(argc==3||argc==4,"Usage: native-replay82 CORE_DIR MAP [CAPTURE_PPM]");if(argc==4)capturePath=argv[3];std::string core=argv[1];
 open_archive(0,core+"/STARDAT.MPQ");open_archive(1,core+"/BROODAT.MPQ");open_archive(2,core+"/patch_rt.mpq");open_archive(3,argv[2]);
 auto ai=fixture82(1,7000,true,false,false,false);exercise82(ai,"broodwar_ai",true);
 auto teams=fixture82(2,4600,true,true,true,false);exercise82(teams,"team_hero_ai",false);
 auto classicRef=fixture82(0,2200,false,false,false,true);renderEveryPump82=true;exercise82(classicRef,"classic_human",false);
 long_pending82(classicRef);native_replay::seek(classicRef.frames);check(native_replay::seeking,"Close precondition lacks pending seek");close_game();
 check(!native_replay::active&&!native_replay::seeking&&native_replay::checkpoints.empty()&&native_replay::cache_bytes()==0,"Closing replay did not release seek work and checkpoints");
 native_replay::seek(100);native_replay::cancel_seek();native_replay::pump_seek();check(!g_ui&&!native_replay::seeking,"Seek control created a game after close");
 crowded_snapshot82();check(peakCheckpoints82==native_replay::MAX_CHECKPOINTS,"Seek fixtures did not reach the checkpoint eviction limit");check(checkpointEvictions82>0&&checkpointRestores82>0,"Seek fixtures did not exercise checkpoint eviction and reuse");
 std::cout<<"PASS intermediate seek rendering preserves simulation and AI in the classic human fixture\n";
 std::cout<<"PASS bounded seek pumps="<<seekPumps82<<" requests="<<seekCalls82<<" maxFramesPerPump="<<native_replay::SEEK_BATCH_FRAMES<<" longestHostPumpMs="<<longestPump82<<" peakCacheBytes="<<peakCacheBytes82<<" peakCheckpoints="<<peakCheckpoints82<<" checkpointEvictions="<<checkpointEvictions82<<" checkpointRestores="<<checkpointRestores82<<"\n";
 std::cout<<"PASS "<<checks<<" checks in "<<scenarios<<" replay seek scenarios\n";return 0;
 }catch(const std::exception& e){std::cerr<<"FAIL: "<<e.what()<<"\n";return 1;}}
