// Android and host-test adapter. The simulation is based on the retained 0.22 engine plus reviewed native fixes.
#include <cstdio>
#include <stdexcept>
#include <string>
#include <memory>
#include <vector>
#include <array>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include "audio.h"
#include "../vendor/libsmacker/smacker.h"
struct Archive {
 int fd=-1;const uint8_t* data=nullptr;size_t size=0;
 ~Archive(){close();}
 void close(){if(data)munmap((void*)data,size);if(fd>=0)::close(fd);data=nullptr;fd=-1;size=0;}
 void open(const std::string& path){close();if(path.empty())return;fd=::open(path.c_str(),O_RDONLY);if(fd<0)throw std::runtime_error("Cannot open "+path);struct stat s;if(fstat(fd,&s)||s.st_size<32)throw std::runtime_error("Invalid archive");size=s.st_size;auto* p=mmap(nullptr,size,PROT_READ,MAP_PRIVATE,fd,0);if(p==MAP_FAILED)throw std::runtime_error("Cannot map archive");data=(const uint8_t*)p;}
 void read(void* dest,size_t offset,size_t count)const{if(!data||offset>size||count>size-offset)throw std::runtime_error("Archive read outside bounds");memcpy(dest,data+offset,count);}
};
static std::array<Archive,11> archives;
// One original-script opponent; -1 selects a practice game.
static int custom_bot_slot=1;
static int custom_ai_race=1; // Zerg=0, Terran=1, Protoss=2; original-script AI for all races.
static int touch_radius=28;
static bool touch_attack_move=true;
static bool touch_ground_orders=true;
static bool nearby_defenders=true;
static bool replay_seek_audio_muted=false;
extern "C" {
uint8_t* openbw_framebuffer();
int openbw_framebuffer_width();
int openbw_framebuffer_height();
void openbw_mouse_move(int,int);
void openbw_mouse_button(int,int,int,int,int);
void openbw_key(int,int,int);
void openbw_reset_input();
void openbw_command_button(int,int);
void js_read_data(int index,void* dst,unsigned offset,unsigned n){if(index<0||index>=11)throw std::runtime_error("Invalid archive index");archives[index].read(dst,offset,n);}
unsigned js_file_size(int i){return i>=0&&i<11?unsigned(archives[i].size):0;}
int js_sound_load(const void* data,unsigned n){return naudio::add(data,n);}
void js_sound_play(int id,int ch,int vol,int pan){if(!replay_seek_audio_muted)naudio::play(id,ch,vol,pan);}
int js_sound_is_playing(int ch){return naudio::playing(ch);}
void js_sound_stop(int ch){naudio::stop(ch);}
void js_sound_set_volume(int ch,int vol){std::lock_guard<std::mutex> l(naudio::mutex);if(ch>=0&&ch<64)naudio::voices[ch].left=naudio::voices[ch].right=vol/128.f;}
}
#include "generated/wasm_main.cpp"
#include "hero_mode.h"
#include "save_state.h"
static bool net_active=false;
namespace native_replay {static bool is_active();static void step();static void reset();static void begin();}

static void native_step(){
 if(native_replay::is_active()){native_replay::step();return;}
 if(net_active)return;
 if(!g_ui||g_ui->outcome)return;
 // Input is a stream of BW commands; the native host must consume it just as
 // the previous lockstep host did, even in a single-player campaign.
 auto input=std::move(g_ui->outgoing);g_ui->outgoing.clear();
 if(!input.empty())g_ui->apply_commands(g_ui->my_player,input.data(),input.size());
 openbw_step();
}
// The map is reloaded by Java first; the snapshot then replaces its live state.
static std::vector<uint8_t> save_game() {
 native_save::check(g_ui!=nullptr,"No game is running");native_save::check(!native_replay::is_active(),"Cannot save a replay as a game.");native_save::check(!net_active,"Save/load is unavailable during nearby multiplayer");native_save::HostState host;host.bot_slot=custom_bot_slot;
 return native_save::save(*g_ui,g_campaign,host);
}
static void restore_game(std::vector<uint8_t>& bytes) {
 native_save::check(!native_replay::is_active(),"Cannot load a saved game during replay playback.");
 native_save::check(g_ui!=nullptr,"No game is running");native_save::check(!net_active,"Save/load is unavailable during nearby multiplayer");
 auto host=native_save::restore(*g_ui,g_campaign,bytes);
 g_ui->set_test_invincible(false);
 g_ui->set_test_unlimited_supply(false);
 g_ui->rep_on=false; // A resumed world is not a replay starting at frame zero.
 custom_bot_slot=host.bot_slot;
 // One-shot effects from the discarded world must not bleed into a loaded save.
 for(int ch=0;ch<57;ch++)naudio::stop(ch);
 naudio::stop(62);
 for(auto& ch:g_ui->sound_channels)ch.playing=false;
}
struct AssetReader {
 size_t index=0,position=0;
 explicit AssetReader(bwgame::a_string name){index=std::stoul(name.c_str());}
 void get_bytes(uint8_t* dst,size_t n){archives[index].read(dst,position,n);position+=n;}
 void seek(size_t n){position=n;}size_t tell(){return position;}size_t size(){return archives[index].size;}
};
using NativeMPQ=bwgame::data_loading::mpq_file<AssetReader>;
static std::array<std::unique_ptr<NativeMPQ>,11> readers;
static void close_game(){
 native_replay::reset();
 net_active=false;
 if(g_ui){g_ui->wnd.destroy();if(g_campaign)delete g_campaign;else delete g_ui;}
 g_ui=nullptr;g_campaign=nullptr;delete g_mission;g_mission=nullptr;delete g_loader;g_loader=nullptr;g_mission_sounds.clear();naudio::clear_game();naudio::stop(62);
}
#include "multiplayer.h"
#include "replay.h"
static void open_archive(int i,const std::string& path){if(i<0||i>=11)throw std::runtime_error("Invalid archive slot");readers[i].reset();archives[i].open(path);if(archives[i].data)readers[i].reset(new NativeMPQ(std::to_string(i).c_str()));}
static bwgame::a_vector<uint8_t> read_asset(int index,const std::string& filename){
 bwgame::a_vector<uint8_t> data;
 auto read=[&](int i){auto* a=readers[i].get();if(!a||!a->mpq.file_exists(filename.c_str()))return false;
  auto* h=a->mpq.find_hash_table_entry(filename.c_str());if(!h||h->block_index>=a->mpq.block_table.size())return false;const auto& b=a->mpq.block_table[h->block_index];
  if(b.size>128*1024*1024)throw std::runtime_error("Asset too large");
  if(!(b.flags&0x300)&&!(b.flags&0x10000)){if(b.size!=b.compressed_size)throw std::runtime_error("Invalid asset size");data.resize(b.size);archives[i].read(data.data(),b.data_offset,b.size);}
  else (*a)(data,filename.c_str());return true;};
 if(index>=0){if(index>=11)throw std::runtime_error("Invalid asset slot");read(index);}else for(int i:{2,1,0,4})if(read(i))break;
 return data;
}
static const char* text_query(int id){if(id==1&&native_replay::is_active())return native_replay::resources();if(id==0&&native_replay::is_active()){static std::string name;name=openbw_card();name=name.substr(0,name.find('\n'));return name.c_str();}switch(id){
 case 0:return openbw_card();case 1:return openbw_resources();case 2:return openbw_status();case 3:return openbw_wires();case 4:return openbw_unit_details();case 5:return openbw_campaign_events();case 6:return openbw_campaign_status();case 7:return openbw_campaign_timer();case 8:return openbw_error();case 9:return openbw_stats();case 10:return openbw_debug_units();case 11:return openbw_campaign_ai_error();case 12:return openbw_messages();case 13:return openbw_debug_dump();case 14:return g_ui?g_ui->cargo_status():"";default:return "";}}
static int value_query(int id,int n){if(native_replay::is_active()&&id==0)return 0;switch(id){case 30:return g_ui?g_ui->production_portrait():-1;case 25:return native_replay::is_active();case 26:return native_replay::playing.frames;case 27:return native_replay::viewpoint;case 28:return native_replay::seeking;case 29:return native_replay::seek_target;case 0:return openbw_cursor();case 1:return openbw_selected_type();case 2:return openbw_production_icon();case 3:return openbw_production_kind();case 4:return openbw_production_queue_type(n);case 5:return openbw_selected_race();case 6:return openbw_wire_box();case 7:return openbw_outcome();case 8:return openbw_frame();case 9:return openbw_map_w();case 10:return openbw_map_h();case 11:return openbw_portrait_for_type(n);case 12:return openbw_portrait_talking();case 13:return openbw_map_tileset();case 14:return openbw_minimap_terrain();case 16:return openbw_placement_valid((n>>16)&65535,n&65535);case 17:return openbw_touch_action((n>>16)&65535,n&65535);case 18:return g_ui&&g_ui->show_order_lines;case 19:return int(naudio::position_ms(n));case 20:return nearby_defenders;case 21:return g_ui?g_ui->group_count(n):0;case 22:return g_ui&&g_ui->test_invincible;case 23:return openbw_cursor_box();case 24:return g_ui&&g_ui->test_unlimited_supply;default:return 0;}}
static bool touch_beacon_move(int x,int y){
 if(!g_ui||g_ui->paused||g_ui->campaign_control_lock||g_ui->spectator||g_ui->pending_build||g_ui->targeting)return false;
 auto* beacon=g_ui->select_get_unit_at(g_ui->screen_to_map(x,y));
 if(!beacon||!beacon->sprite||g_ui->unit_hidden_by_fog(beacon)||!g_ui->unit_is_special_beacon(beacon))return false;
 auto* selected=g_ui->primary_selected();
 if(!selected||selected->owner!=g_ui->my_player||g_ui->unit_dying(selected))return false;
 g_ui->refresh_card();
 bool mobile=false;for(const auto& c:g_ui->card)if(c.act==bwgame::play_ui::C_MOVE&&c.enabled)mobile=true;
 if(!mobile)return false;
 // Beacons are destinations, including owned ones. Use their center rather
 // than a unit-target Follow order, which can stop outside the trigger area.
 auto destination=beacon->sprite->position;
 g_ui->sync_selection();g_ui->cmd_order(bwgame::Orders::Move,destination,nullptr,g_ui->key_shift());
 g_ui->show_marker(destination);
 g_ui->play_unit_ack(selected,selected->unit_type->first_yes_sound,selected->unit_type->last_yes_sound);
 return true;
}
static void command(int op,int a,int b,int c,int d){
 if(native_replay::is_active()){
  if(op==31){native_replay::set_view(a);return;}
  if(op==4)d=1;
  if(op==1&&b!=1)return;
  if(op==33&&b!=1)return;
  if(op==32&&a&&c!=224&&c!=225&&c!=228&&c!=229&&c!=43&&c!=44)return;
  if(op!=0&&op!=1&&op!=3&&op!=4&&op!=5&&op!=6&&op!=8&&op!=19&&op!=21&&op!=22&&op!=26&&op!=27&&op!=28&&op!=32&&op!=33&&op!=34)return;
 }
 if(net_active && ((op>=9&&op<=18&&op!=12)||op==20||op==23||op==29||op==30))return;
 if(g_ui&&(op==9||op==11||op==13||op==15||op==16||op==17||op==18))g_ui->rep_on=false;
 switch(op){
 case 0:openbw_mouse_move(a,b);break;case 1:openbw_mouse_button(a,b,c,d,1);break;case 2:openbw_key(1,a,b);openbw_key(0,a,b);break;case 3:openbw_pan(a,b);break;
 // Desktop input keeps real press/release lifetimes and double-click counts.
 // Coordinates come from op 0; input still uses the existing BW command stream.
 case 32:openbw_key(a!=0,b,c);break;
 case 33:if(g_ui&&(b==1||b==3)){int x,y;g_ui->wnd.get_cursor_pos(&x,&y);openbw_mouse_button(a!=0,b,x,y,c>=2?2:1);}break;
 case 34:openbw_reset_input();openbw_touch_cancel();if(g_ui)g_ui->is_moving_replay_slider=false;break;
 case 35:openbw_command_button(a,b);break;
 case 4:{
  if(!d&&touch_beacon_move(a,b))break;
  if (g_ui) if (auto* near=g_ui->touch_selection_near(a,b,std::max(0,int(touch_radius/std::max(.5,g_ui->minimap_scale))),d!=0)) {
   g_ui->current_selection_clear();g_ui->current_selection_add(near);
   if(c && near->owner==g_ui->my_player && !g_ui->ut_building(near))
    for(auto* other:g_ui->find_units({g_ui->screen_pos,g_ui->screen_pos+bwgame::xy(g_ui->view_width,g_ui->view_height)}))
     if(other!=near && other->owner==near->owner && other->unit_type==near->unit_type
       && g_ui->unit_can_be_selected(other) && !g_ui->unit_hidden_by_fog(other))g_ui->current_selection_add(other);
   g_ui->on_selection(c!=0);break;
  }
  int action=d?0:openbw_touch_action(a,b);
  if(g_ui && action!=2 && !touch_ground_orders) {
   auto* hit=g_ui->select_get_unit_at(g_ui->screen_to_map(a,b));
   if(!hit||g_ui->unit_hidden_by_fog(hit))action=0;
  }
  if(action==3 && touch_attack_move)openbw_touch_attack();int button=(action==1 || (action==3 && !touch_attack_move))?3:1;openbw_mouse_move(a,b);openbw_mouse_button(1,button,a,b,c?2:1);openbw_mouse_button(0,button,a,b,c?2:1);break;}
 case 22:touch_radius=std::max(0,std::min(40,a));touch_attack_move=b!=0;touch_ground_orders=c!=0;break;
 case 23:nearby_defenders=a!=0;if(g_campaign&&g_campaign->base_defence_enabled!=nearby_defenders){g_campaign->base_defence_enabled=nearby_defenders;if(!nearby_defenders)g_campaign->defence_alerts={};if(g_ui->st.current_frame)g_ui->rep_on=false;else native_replay::begin();}break;
 case 26:if(g_ui)g_ui->host_controls_edge_scroll=a!=0;break;
 case 27:if(g_ui)g_ui->original_larvae_selection=a!=0;break;
 case 28:if(g_ui)g_ui->single_larva_morph=a!=0;break;
 case 29:if(g_ui)g_ui->set_test_invincible(a!=0);break;
 case 30:if(g_ui)g_ui->set_test_unlimited_supply(a!=0);break;
 case 25:if(g_ui && !g_ui->paused && !g_ui->campaign_control_lock && a>=0 && a<10){g_ui->clear_pending();if(b)g_ui->assign_group(a);else g_ui->recall_group(a);}break;
 case 24:if(g_ui){g_ui->sync_selection();g_ui->cmds.unload(uint16_t(a));}break;
 case 5:openbw_touch_cancel();break;case 6:openbw_set_paused(a);break;case 7:openbw_cancel(a);break;case 8:openbw_set_minimap_terrain(a);break;case 9:openbw_test_money();break;case 10:openbw_test_instant(a);break;case 11:openbw_reveal_map();break;case 12:openbw_resign();break;case 13:openbw_debug_outcome(a);break;case 14:openbw_debug_select(a,b);break;case 15:openbw_debug_set_life(a,b,c);break;case 16:openbw_debug_kill(a);break;case 17:openbw_debug_resources(a,b);break;case 18:openbw_debug_warp(a,b,c);break;case 19:openbw_set_order_lines(a);break;case 21:if(g_ui){g_ui->hud_bottom_inset=std::max(0,a);g_ui->clamp_viewport();}break;case 20:custom_bot_slot=std::max(-1,std::min(7,a));custom_ai_race=std::max(0,std::min(2,b));g_terran_ai_level=std::max(0,std::min(2,c));g_skirmish_expansion=d!=1;break;
 }}
struct Movie {bwgame::a_vector<uint8_t> bytes;smk handle=nullptr;unsigned long width=0,height=0,frames=0,current=0;double us=100000;bool audio=false;std::vector<uint8_t> rgba;~Movie(){if(audio)naudio::stop(60);if(handle)smk_close(handle);}};
static std::array<std::unique_ptr<Movie>,24> movies;
static int open_movie(const uint8_t* data,size_t size,bool audio=false){
 int slot=-1;for(int i=0;i<24;i++)if(!movies[i]){slot=i;break;}if(slot<0)throw std::runtime_error("Too many movie streams");
 if(size<104||size>128*1024*1024||(memcmp(data,"SMK2",4)&&memcmp(data,"SMK4",4)))throw std::runtime_error("Invalid movie");
 auto m=std::unique_ptr<Movie>(new Movie);m->audio=audio;m->handle=smk_open_memory(data,size);if(!m->handle)throw std::runtime_error("Cannot decode Smacker video");
 unsigned char scale=0;smk_info_video(m->handle,&m->width,&m->height,&scale);smk_info_all(m->handle,nullptr,&m->frames,&m->us);
 if(m->width>1024||m->height>768||!m->width||!m->height||!m->frames)throw std::runtime_error("Unsupported movie dimensions");
 if(audio){unsigned char mask=0,ch[7]={},bits[7]={};unsigned long rates[7]={};smk_info_audio(m->handle,&mask,ch,bits,rates);
  for(int track=0;track<7;track++)if(mask&(1<<track)){
   if(ch[track]<1||ch[track]>2||(bits[track]!=8&&bits[track]!=16)||rates[track]<8000||rates[track]>48000)throw std::runtime_error("Unsupported movie audio");
   auto clip=std::make_shared<naudio::Clip>();clip->rate=rates[track];clip->channels=ch[track];smk_enable_all(m->handle,1<<track);smk_first(m->handle);
   for(unsigned long frame=0;frame<m->frames;frame++){const auto* pcm=smk_get_audio(m->handle,track);size_t n=smk_get_audio_size(m->handle,track)/(bits[track]/8);if(n>8*1024*1024||clip->pcm.size()+n>64*1024*1024)throw std::runtime_error("Movie audio is too long");for(size_t i=0;i<n;i++)clip->pcm.push_back(bits[track]==16?int16_t(naudio::u16(pcm+i*2))/32768.f:(int(pcm[i])-128)/128.f);if(frame+1<m->frames&&smk_next(m->handle)<0)break;}
   if(!clip->pcm.empty())naudio::play_clip(clip,60);break;
  }
 }
 smk_enable_all(m->handle,SMK_VIDEO_TRACK);smk_first(m->handle);m->rgba.resize(m->width*m->height*4);movies[slot]=std::move(m);return slot;
}
static Movie* movie_frame(int id,double time,bool loop){
 if(id<0||id>=24||!movies[id])return nullptr;auto* m=movies[id].get();unsigned long f=(unsigned long)(std::max(0.,time)*1000/std::max(1000.,m->us));f=loop?f%m->frames:std::min(f,m->frames-1);
 if(f<m->current){smk_first(m->handle);m->current=0;}while(m->current<f){if(smk_next(m->handle)<0)break;++m->current;}
 const auto* p=smk_get_palette(m->handle);const auto* b=smk_get_video(m->handle);for(size_t i=0;i<m->width*m->height;i++){int k=b[i]*3;m->rgba[4*i]=p[k];m->rgba[4*i+1]=p[k+1];m->rgba[4*i+2]=p[k+2];m->rgba[4*i+3]=m->audio||b[i]?255:0;}return m;
}
#ifdef __ANDROID__
#include <jni.h>
#include <android/bitmap.h>
#define JNI(name) extern "C" JNIEXPORT
static void fail(JNIEnv* e,const std::exception& ex){e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),ex.what());}
static std::string utf(JNIEnv* e,jstring s){if(!s)return "";const char* p=e->GetStringUTFChars(s,nullptr);std::string out(p);e->ReleaseStringUTFChars(s,p);return out;}
static jbyteArray bytes(JNIEnv* e,const uint8_t* p,size_t n){jbyteArray a=e->NewByteArray(n);if(n)e->SetByteArrayRegion(a,0,n,(const jbyte*)p);return a;}
static void bitmap(JNIEnv* e,jobject target,const uint8_t* p,int w,int h){if(!target||!p)return;AndroidBitmapInfo info;if(AndroidBitmap_getInfo(e,target,&info)||info.width!=unsigned(w)||info.height!=unsigned(h)||info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888)throw std::runtime_error("Bitmap dimensions differ");void* out=nullptr;if(AndroidBitmap_lockPixels(e,target,&out))throw std::runtime_error("Bitmap is unavailable");for(int y=0;y<h;y++)memcpy((uint8_t*)out+y*info.stride,p+size_t(y)*w*4,size_t(w)*4);AndroidBitmap_unlockPixels(e,target);}
JNI(open) void JNICALL Java_org_openbw_androidtest_NativeBridge_open(JNIEnv* e,jclass,jint i,jstring path){try{open_archive(i,utf(e,path));}catch(const std::exception& x){fail(e,x);}}
JNI(close) void JNICALL Java_org_openbw_androidtest_NativeBridge_close(JNIEnv*,jclass){close_game();for(auto& m:movies)m.reset();for(int i=0;i<11;i++){readers[i].reset();archives[i].close();}naudio::clear();}
JNI(init) void JNICALL Java_org_openbw_androidtest_NativeBridge_init(JNIEnv* e,jclass,jint w,jint h,jint code,jint race){try{close_game();if(code>0)openbw_init_campaign(w,h,code);else {
 if(custom_bot_slot>0)openbw_init_race_ai(w,h,race,custom_bot_slot,custom_ai_race);
 else openbw_init(w,h,race,0);
 }if(g_campaign)g_campaign->base_defence_enabled=nearby_defenders;openbw_set_external_cursor(1);openbw_mouse_move(-100,-100);}catch(const std::exception& x){close_game();fail(e,x);}}
static std::vector<uint8_t> net_java_bytes(JNIEnv* e,jbyteArray a,size_t limit=16384) {
 if(!a||size_t(e->GetArrayLength(a))>limit)throw std::runtime_error("Invalid byte payload size");
 std::vector<uint8_t> b(e->GetArrayLength(a));if(!b.empty())e->GetByteArrayRegion(a,0,b.size(),(jbyte*)b.data());return b;
}
JNI(multiplayerInit) jlongArray JNICALL Java_org_openbw_androidtest_NativeBridge_multiplayerInit(JNIEnv* e,jclass,jint w,jint h,jint slot,jint first,jint second,jboolean expansion){try{
 net_init(w,h,slot,first,second,expansion);jlong values[]={jlong(net_assets()),jlong(net_hash())};auto a=e->NewLongArray(2);e->SetLongArrayRegion(a,0,2,values);return a;
 }catch(const std::exception& x){close_game();fail(e,x);return nullptr;}}
JNI(configuredInit) jlongArray JNICALL Java_org_openbw_androidtest_NativeBridge_configuredInit(JNIEnv* e,jclass,jint slot,jintArray r,jintArray c,jint level,jboolean expansion,jboolean ffa,jboolean network,jintArray team,jboolean teamGame,jboolean heroes){try {
 if(!r||!c||e->GetArrayLength(r)!=8||e->GetArrayLength(c)!=8)throw std::runtime_error("Invalid player slots");
 jint races[8],controllers[8];e->GetIntArrayRegion(r,0,8,races);e->GetIntArrayRegion(c,0,8,controllers);
 if(!team||e->GetArrayLength(team)!=8)throw std::runtime_error("Invalid teams");
 jint teams[8];e->GetIntArrayRegion(team,0,8,teams);
 configured_init(640,480,slot,races,controllers,level,expansion,ffa,network,teams,teamGame,heroes);if(g_campaign)g_campaign->base_defence_enabled=network||nearby_defenders;
 jlong values[]={jlong(net_assets()),network?jlong(net_hash()):0};auto a=e->NewLongArray(2);e->SetLongArrayRegion(a,0,2,values);return a;
 }catch(const std::exception& x){close_game();fail(e,x);return nullptr;}}
JNI(multiplayerSave) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_multiplayerSave(JNIEnv* e,jclass){try{auto data=net_save();return bytes(e,data.data(),data.size());}catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(multiplayerRestore) void JNICALL Java_org_openbw_androidtest_NativeBridge_multiplayerRestore(JNIEnv* e,jclass,jbyteArray b){try {
 if(!b||e->GetArrayLength(b)>native_save::MAX_BYTES)throw std::runtime_error("Invalid saved game");std::vector<uint8_t> data(e->GetArrayLength(b));e->GetByteArrayRegion(b,0,data.size(),(jbyte*)data.data());net_restore(data);
 }catch(const std::exception& x){fail(e,x);}}
JNI(multiplayerCollect) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_multiplayerCollect(JNIEnv* e,jclass){try{
 if(!net_active||!g_ui)throw std::runtime_error("No multiplayer game");auto& b=g_ui->outgoing;net_validate(b.data(),b.size());auto a=bytes(e,b.data(),b.size());b.clear();return a;
 }catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(multiplayerHash) jlong JNICALL Java_org_openbw_androidtest_NativeBridge_multiplayerHash(JNIEnv* e,jclass){try{return net_hash();}catch(const std::exception& x){fail(e,x);return 0;}}
JNI(multiplayerApply) void JNICALL Java_org_openbw_androidtest_NativeBridge_multiplayerApply(JNIEnv* e,jclass,jbyteArray a,jbyteArray b){try{
 auto first=net_java_bytes(e,a),second=net_java_bytes(e,b);net_apply(first.data(),first.size(),second.data(),second.size());
 }catch(const std::exception& x){fail(e,x);}}
JNI(multiplayerApplyAll) void JNICALL Java_org_openbw_androidtest_NativeBridge_multiplayerApplyAll(JNIEnv* e,jclass,jobjectArray batches){try {
 if(!batches||e->GetArrayLength(batches)!=8)throw std::runtime_error("Invalid multiplayer slots");
 std::array<std::vector<uint8_t>,8> commands;
 for(int i=0;i<8;i++){auto a=(jbyteArray)e->GetObjectArrayElement(batches,i);commands[i]=net_java_bytes(e,a);e->DeleteLocalRef(a);}
 net_apply_all(commands);
 }catch(const std::exception& x){fail(e,x);}}
JNI(multiplayerStep) void JNICALL Java_org_openbw_androidtest_NativeBridge_multiplayerStep(JNIEnv* e,jclass){try{net_step();}catch(const std::exception& x){fail(e,x);}}
JNI(multiplayerPeerLeft) void JNICALL Java_org_openbw_androidtest_NativeBridge_multiplayerPeerLeft(JNIEnv* e,jclass){try{net_peer_left();}catch(const std::exception& x){fail(e,x);}}
JNI(resize) void JNICALL Java_org_openbw_androidtest_NativeBridge_resize(JNIEnv* e,jclass,jint w,jint h,jdouble scale){try{openbw_resize(w,h);openbw_set_view_scale(scale);}catch(const std::exception& x){fail(e,x);}}
JNI(frame) void JNICALL Java_org_openbw_androidtest_NativeBridge_frame(JNIEnv* e,jclass,jobject b,jint steps,jdouble time){try{if(native_replay::seeking)native_replay::pump_seek();else for(int i=0;i<steps;i++)native_step();openbw_render(time);bitmap(e,b,openbw_framebuffer(),openbw_framebuffer_width(),openbw_framebuffer_height());}catch(const std::exception& x){fail(e,x);}}
JNI(text) jstring JNICALL Java_org_openbw_androidtest_NativeBridge_text(JNIEnv* e,jclass,jint id){try{return e->NewStringUTF(text_query(id));}catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(rawText) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_rawText(JNIEnv* e,jclass,jint id){try{const char* s=text_query(id);return bytes(e,reinterpret_cast<const uint8_t*>(s),strlen(s));}catch(const std::exception& x){fail(e,x);return nullptr;}}
// Independent reader: background map imports never replace the live game's archive.
JNI(mapScenario) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_mapScenario(JNIEnv* e,jclass,jstring path){try{
 bwgame::data_loading::mpq_file<> archive(utf(e,path).c_str());const char* name="staredit\\scenario.chk";
 auto* h=archive.mpq.find_hash_table_entry(name);if(!h)return nullptr;
 if(h->block_index>=archive.mpq.block_table.size()||archive.mpq.block_table[h->block_index].size>64*1024*1024)throw std::runtime_error("Map scenario exceeds 64 MB.");
 bwgame::a_vector<uint8_t> chk;archive(chk,name);return bytes(e,chk.data(),chk.size());
 }catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(archiveText) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_archiveText(JNIEnv* e,jclass,jstring path){try{
 bwgame::data_loading::mpq_file<> archive(utf(e,path).c_str());const char* name="rez\\stat_txt.tbl";
 auto* h=archive.mpq.find_hash_table_entry(name);if(!h)return nullptr;
 if(h->block_index>=archive.mpq.block_table.size()||archive.mpq.block_table[h->block_index].size>1024*1024)throw std::runtime_error("Invalid language table");
 bwgame::a_vector<uint8_t> table;archive(table,name);return bytes(e,table.data(),table.size());
 }catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(value) jint JNICALL Java_org_openbw_androidtest_NativeBridge_value(JNIEnv* e,jclass,jint id,jint n){try{if(id==15)return g_ui&&g_ui->rep_on&&native_replay::recording.present&&!native_replay::is_active();return value_query(id,n);}catch(const std::exception& x){fail(e,x);return 0;}}
JNI(saveState) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_saveState(JNIEnv* e,jclass){try{auto data=save_game();return bytes(e,data.data(),data.size());}catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(restoreState) void JNICALL Java_org_openbw_androidtest_NativeBridge_restoreState(JNIEnv* e,jclass,jbyteArray b){try{if(!b)throw std::runtime_error("Missing saved game");size_t n=e->GetArrayLength(b);native_save::check(n<=native_save::MAX_BYTES);std::vector<uint8_t> data(n);e->GetByteArrayRegion(b,0,n,(jbyte*)data.data());restore_game(data);}catch(const std::exception& x){fail(e,x);}}
JNI(seekSound) void JNICALL Java_org_openbw_androidtest_NativeBridge_seekSound(JNIEnv*,jclass,jint ch,jint ms){std::lock_guard<std::mutex> l(naudio::mutex);if(ch<0||ch>=64||ms<0)return;auto& v=naudio::voices[ch];if(v.clip)v.position=std::min(double(v.clip->pcm.size()/v.clip->channels),double(ms)*v.clip->rate/1000.);}
JNI(soundPosition) jdouble JNICALL Java_org_openbw_androidtest_NativeBridge_soundPosition(JNIEnv*,jclass,jint ch){return ch>=0&&ch<64?naudio::position_ms(ch):-1;}
JNI(scoreSound) jint JNICALL Java_org_openbw_androidtest_NativeBridge_scoreSound(JNIEnv* e,jclass,jbyteArray fill,jbyteArray ending,jint duration){try{
 auto f=net_java_bytes(e,fill,8*1024*1024),t=net_java_bytes(e,ending,8*1024*1024);
 auto a=naudio::wav(f.data(),f.size()),b=naudio::wav(t.data(),t.size());
 naudio::play_clip(naudio::score_clip(*a,*b,duration),57);return duration;
 }catch(const std::exception& x){fail(e,x);return 0;}}
JNI(replay) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_replay(JNIEnv* e,jclass){try{auto b=native_replay::save();return b.empty()?nullptr:bytes(e,b.data(),b.size());}catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(replayInfo) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_replayInfo(JNIEnv* e,jclass,jbyteArray data){try{auto b=net_java_bytes(e,data,native_replay::MAX_FILE);auto info=native_replay::info(b.data(),b.size());return bytes(e,reinterpret_cast<const uint8_t*>(info.data()),info.size());}catch(const std::exception& x){fail(e,x);return nullptr;}}
// Read-only preview: use the same bounded decoder/validation as playback,
// without opening a game, mounting a map or changing the active replay.
JNI(replayScenario) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_replayScenario(JNIEnv* e,jclass,jbyteArray data){try{auto b=net_java_bytes(e,data,native_replay::MAX_FILE);auto replay=native_replay::parse(b.data(),b.size());return bytes(e,replay.map.data(),replay.map.size());}catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(replayOpen) void JNICALL Java_org_openbw_androidtest_NativeBridge_replayOpen(JNIEnv* e,jclass,jbyteArray data){try{auto b=net_java_bytes(e,data,native_replay::MAX_FILE);native_replay::open(b.data(),b.size());}catch(const std::exception& x){fail(e,x);}}
JNI(replaySeek) void JNICALL Java_org_openbw_androidtest_NativeBridge_replaySeek(JNIEnv* e,jclass,jint target){try{native_replay::seek(target);}catch(const std::exception& x){fail(e,x);}}
JNI(replayCancelSeek) void JNICALL Java_org_openbw_androidtest_NativeBridge_replayCancelSeek(JNIEnv* e,jclass){try{native_replay::cancel_seek();}catch(const std::exception& x){fail(e,x);}}
JNI(command) void JNICALL Java_org_openbw_androidtest_NativeBridge_command(JNIEnv* e,jclass,jint op,jint a,jint b,jint c,jint d){try{command(op,a,b,c,d);}catch(const std::exception& x){fail(e,x);}}
JNI(asset) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_asset(JNIEnv* e,jclass,jint slot,jstring name){try{auto data=read_asset(slot,utf(e,name));return data.empty()?nullptr:bytes(e,data.data(),data.size());}catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(chk) jbyteArray JNICALL Java_org_openbw_androidtest_NativeBridge_chk(JNIEnv* e,jclass){return bytes(e,openbw_campaign_chk(),openbw_campaign_chk_len());}
JNI(sound) jint JNICALL Java_org_openbw_androidtest_NativeBridge_sound(JNIEnv* e,jclass,jbyteArray a,jint channel,jboolean loop){try{if(!a){naudio::stop(channel);return 0;}std::vector<uint8_t> b(e->GetArrayLength(a));e->GetByteArrayRegion(a,0,b.size(),(jbyte*)b.data());int id=naudio::add(b.data(),b.size());naudio::play(id,channel,128,0,loop);std::lock_guard<std::mutex> lock(naudio::mutex);auto c=naudio::clips.back();int length=int(1000.*c->pcm.size()/c->channels/c->rate);naudio::clips.pop_back();return length;}catch(const std::exception& x){fail(e,x);return 0;}}
JNI(campaignSound) jint JNICALL Java_org_openbw_androidtest_NativeBridge_campaignSound(JNIEnv* e,jclass,jint i){try{const auto& b=mission_sound(i);if(b.empty())return 0;int id=naudio::add(b.data(),b.size());naudio::play(id,62,128,0);std::lock_guard<std::mutex> lock(naudio::mutex);auto c=naudio::clips.back();int length=int(1000.*c->pcm.size()/c->channels/c->rate);naudio::clips.pop_back();return length;}catch(const std::exception& x){fail(e,x);return 0;}}
JNI(campaignEffect) void JNICALL Java_org_openbw_androidtest_NativeBridge_campaignEffect(JNIEnv* e,jclass,jint i){try{
 if(i<=0||naudio::effect_playing(i))return;const auto& b=mission_sound(i);if(b.empty())return;
 naudio::play_effect(naudio::wav(b.data(),b.size()),i);
 }catch(const std::exception& x){fail(e,x);}}
JNI(mix) void JNICALL Java_org_openbw_androidtest_NativeBridge_mix(JNIEnv* e,jclass,jshortArray a,jint rate){jshort* p=e->GetShortArrayElements(a,nullptr);naudio::mix(p,e->GetArrayLength(a)/2,rate);e->ReleaseShortArrayElements(a,p,0);}
JNI(audio) void JNICALL Java_org_openbw_androidtest_NativeBridge_audio(JNIEnv*,jclass,jboolean paused,jfloat effects,jfloat music){std::lock_guard<std::mutex> l(naudio::mutex);naudio::paused=paused;naudio::effects=effects;naudio::music=music;}
JNI(playing) jboolean JNICALL Java_org_openbw_androidtest_NativeBridge_playing(JNIEnv*,jclass,jint ch){return naudio::playing(ch);}
JNI(wire) void JNICALL Java_org_openbw_androidtest_NativeBridge_wire(JNIEnv* e,jclass,jobject b,jint n){try{const uint8_t* p=openbw_wire_rgba(n);int box=openbw_wire_box();bitmap(e,b,p,box,box);}catch(const std::exception& x){fail(e,x);}}
JNI(cargoWire) void JNICALL Java_org_openbw_androidtest_NativeBridge_cargoWire(JNIEnv* e,jclass,jobject b,jint n){try{const uint8_t* p=g_ui?g_ui->render_cargo_wireframe(size_t(n)):nullptr;bitmap(e,b,p,64,64);}catch(const std::exception& x){fail(e,x);}}
JNI(cursor) jboolean JNICALL Java_org_openbw_androidtest_NativeBridge_cursor(JNIEnv* e,jclass,jobject b){try{const uint8_t* p=openbw_cursor_rgba();if(!p)return false;int box=openbw_cursor_box();bitmap(e,b,p,box,box);return true;}catch(const std::exception& x){fail(e,x);return false;}}
JNI(movieOpenAsset) jintArray JNICALL Java_org_openbw_androidtest_NativeBridge_movieOpenAsset(JNIEnv* e,jclass,jint slot,jstring path,jboolean audio){try{auto b=read_asset(slot,utf(e,path));int id=open_movie(b.data(),b.size(),audio);auto& m=*movies[id];int values[]={id,int(m.width),int(m.height),int(m.frames),int(m.us/1000)};jintArray v=e->NewIntArray(5);e->SetIntArrayRegion(v,0,5,values);return v;}catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(movieOpen) jintArray JNICALL Java_org_openbw_androidtest_NativeBridge_movieOpen(JNIEnv* e,jclass,jbyteArray a,jboolean audio){try{std::vector<uint8_t> b(e->GetArrayLength(a));e->GetByteArrayRegion(a,0,b.size(),(jbyte*)b.data());int id=open_movie(b.data(),b.size(),audio);auto& m=*movies[id];int values[]={id,int(m.width),int(m.height),int(m.frames),int(m.us/1000)};jintArray v=e->NewIntArray(5);e->SetIntArrayRegion(v,0,5,values);return v;}catch(const std::exception& x){fail(e,x);return nullptr;}}
JNI(endGame) void JNICALL Java_org_openbw_androidtest_NativeBridge_endGame(JNIEnv*,jclass){close_game();}
JNI(movieFrame) void JNICALL Java_org_openbw_androidtest_NativeBridge_movieFrame(JNIEnv* e,jclass,jint id,jdouble time,jboolean loop,jobject b){try{auto* m=movie_frame(id,time,loop);if(m)bitmap(e,b,m->rgba.data(),m->width,m->height);}catch(const std::exception& x){fail(e,x);}}
JNI(movieClose) void JNICALL Java_org_openbw_androidtest_NativeBridge_movieClose(JNIEnv*,jclass,jint id){if(id>=0&&id<24)movies[id].reset();}
#endif
