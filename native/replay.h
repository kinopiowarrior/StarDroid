#pragma once
// Classic .rep playback plus an integrity-checked StarDroid settings footer.
// Computer scripts are simulated locally; the footer preserves their exact setup.
namespace native_replay {
constexpr size_t MAX_FILE=32u*1024*1024, MAX_ACTIONS=16u*1024*1024, MAX_MAP=16u*1024*1024;
constexpr uint64_t MAGIC=0x3130505244445453ull; // STDDRP01
// Simulation rules changed: bounded detector responses and campaign fixes.
constexpr uint32_t ENGINE=85;
struct Rules {
 bool present=false,heroes=false,teams=false,defenders=true;int difficulty=2,player=0;unsigned ai=0;
 uint64_t signature=0,initial=0;
 std::array<std::array<uint8_t,12>,12> alliances{};
 std::array<uint32_t,12> vision{};
};
struct Parsed {
 std::array<uint8_t,633> header{};bwgame::a_vector<uint8_t> actions,map;
 Rules rules;size_t classicSize=0,headerStart=0,headerEnd=0;int frames=0;bool expansion=true;std::string title,mapName;
 std::array<int,12> controllers{},races{};std::array<std::string,12> names{};
};
static Rules recording;
static Parsed playing;
static std::vector<uint8_t> opened;
static bool active=false;
static int viewpoint=-1;
// Checkpoints are disposable accelerators, never replay-file data. Their full
// action cursor and local AI state must travel with the serialized world.
constexpr size_t MAX_CHECKPOINT_BYTES=48u*1024*1024,MAX_CHECKPOINTS=8;
constexpr int CHECKPOINT_INTERVAL=24*30,SEEK_BATCH_FRAMES=128;
struct Checkpoint {int frame=0;std::vector<uint8_t> bytes;};
static std::vector<Checkpoint> checkpoints;
static bool seeking=false,checkpoint_disabled=false;
static int seek_target=0;
static size_t cache_bytes(){size_t n=0;for(auto& c:checkpoints)n+=c.bytes.capacity();return n;}
static void remember_checkpoint();
static bool is_active(){return active;}
static std::string clean(const uint8_t* p,size_t n) {std::string s;for(size_t i=0;i<n&&p[i];i++)if(p[i]>=32&&p[i]!=127)s+=char(p[i]);return s;}
static uint32_t u32(const uint8_t* p){return uint32_t(p[0])|uint32_t(p[1])<<8|uint32_t(p[2])<<16|uint32_t(p[3])<<24;}
static void need(bool yes,const char* why){if(!yes)throw std::runtime_error(why);}
// Validate the command stream without running it or replacing the current game.
// In particular, the engine's debugging action 210 is not a classic replay
// command and can dereference an invalid unit. It must never reach execution.
static void validate_actions(const Parsed& p) {
 std::array<int,256> owners;owners.fill(-1);
 for(int slot=0;slot<12;slot++) {
  uint32_t id=u32(p.header.data()+161+slot*36+4);
  if(id<owners.size()&&owners[id]<0)owners[id]=slot;
  bool active=p.controllers[slot]==bwgame::player_t::controller_occupied||p.controllers[slot]==bwgame::player_t::controller_computer_game;
  if(active)need(slot<8&&id<owners.size()&&owners[id]==slot&&p.races[slot]<3,"Invalid replay player identity or race.");
 }
 for(size_t frameAt=0;frameAt<p.actions.size();) {
  need(p.actions.size()-frameAt>=5,"Truncated replay action frame.");
  need(u32(p.actions.data()+frameAt)<=unsigned(p.frames),"Invalid replay action frame.");
  size_t at=frameAt+5,end=at+p.actions[frameAt+4];
  need(end<=p.actions.size(),"Truncated replay action frame.");
  while(at<end) {
   need(end-at>=2,"Truncated replay command.");
   int owner=owners[p.actions[at++]];unsigned op=p.actions[at++];
   need(owner>=0&&owner<8&&(p.controllers[owner]==bwgame::player_t::controller_occupied||p.controllers[owner]==bwgame::player_t::controller_computer_game),"Replay command refers to an inactive or unknown player.");
   auto bytes=[&](size_t count){need(count<=end-at,"Truncated replay command.");const uint8_t* data=p.actions.data()+at;at+=count;return data;};
   auto word=[](const uint8_t* data){return unsigned(data[0])|(unsigned(data[1])<<8);};
   auto unitType=[&](unsigned type,bool nullable=false){need(type<unsigned(bwgame::UnitTypes::None)||(nullable&&type==unsigned(bwgame::UnitTypes::None)),"Invalid replay unit type.");};
   auto orderType=[&](unsigned type){need(type<unsigned(bwgame::Orders::None),"Invalid replay order type.");};
   switch(op) {
    case 9:case 10:case 11:{unsigned count=bytes(1)[0];need(count<=12,"Invalid replay selection size.");bytes(count*2);break;}
    case 12:{auto d=bytes(7);orderType(d[0]);unitType(word(d+5));break;}
    case 13:bytes(2);break;
    case 14:bytes(4);break;
    case 19:{auto d=bytes(2);need(d[0]<=2&&d[1]<10,"Invalid replay control group.");break;}
    case 20:{auto d=bytes(9);unitType(word(d+6),true);need(d[8]<=1,"Invalid replay queued-order flag.");break;}
    case 21:{auto d=bytes(10);unitType(word(d+6),true);orderType(d[8]);need(d[9]<=1,"Invalid replay queued-order flag.");break;}
    case 24:case 25:case 27:case 28:case 39:case 42:case 46:case 49:case 51:case 52:case 54:case 90:break;
    case 26:case 30:case 37:case 38:case 40:case 43:case 44:need(bytes(1)[0]<=1,"Invalid replay queued-order flag.");break;
    case 31:case 35:case 53:unitType(word(bytes(2)));break;
    case 32:{unsigned slot=word(bytes(2));need(slot<5||slot==254,"Invalid replay training queue slot.");break;}
    case 33:case 34:case 45:case 87:bytes(1);break;
    case 41:bytes(2);break;
    case 47:case 88:bytes(4);break;
    case 48:need(bytes(1)[0]<unsigned(bwgame::TechTypes::None),"Invalid replay technology.");break;
    case 50:need(bytes(1)[0]<unsigned(bwgame::UpgradeTypes::None),"Invalid replay upgrade.");break;
    case 92:bytes(81);break;
    case 18:case 210:throw std::runtime_error("Replays with cheat commands are unsupported.");
    default:throw std::runtime_error("Replay contains an unsupported command.");
   }
  }
  frameAt=end;
 }
}
// Bound the total decoded allocation, including duplicate/overlapping string
// offsets. Sorting offsets lets us locate all terminators in one reverse scan.
static void validate_strings(const uint8_t* data,size_t size,unsigned wordSize) {
 need(size>=wordSize,"Truncated replay string table.");
 auto number=[&](size_t at){return wordSize==4?u32(data+at):unsigned(data[at])|(unsigned(data[at+1])<<8);};
 size_t count=number(0),budget=32u*1024*1024;
 need(count<=(size-wordSize)/wordSize&&count<=budget/sizeof(bwgame::a_string),"Replay string table is too large or truncated.");
 std::vector<uint32_t> offsets;offsets.reserve(count);
 for(size_t i=0;i<count;i++){auto offset=number((i+1)*wordSize);need(offset<size,"Invalid replay string offset.");offsets.push_back(offset);}
 std::sort(offsets.begin(),offsets.end(),std::greater<uint32_t>());
 size_t scan=size,terminator=size,total=count*sizeof(bwgame::a_string);
 for(auto offset:offsets){while(scan>offset){--scan;if(data[scan]==0)terminator=scan;}
  need(terminator<size,"Unterminated replay map string.");size_t length=terminator-offset+1;
  need(length<=budget-total,"Replay string table is too large or truncated.");total+=length;
 }
}
// CHK fields used for allocation need admission bounds before the map loader.
static void validate_map(const Parsed& p) {
 bool dimensions=false;unsigned width=0,height=0;size_t terrain=0;
 for(size_t at=0;at<p.map.size();) {
  need(p.map.size()-at>=8,"Truncated replay map chunk.");auto* tag=p.map.data()+at;size_t size=u32(tag+4);at+=8;
  need(size<=p.map.size()-at,"Truncated replay map chunk.");auto* data=p.map.data()+at;
  if(!memcmp(tag,"DIM ",4)) {
   need(size==4,"Replay map dimensions are missing.");width=unsigned(data[0])|(unsigned(data[1])<<8);height=unsigned(data[2])|(unsigned(data[3])<<8);
   need(width&&height&&width<=256&&height<=256,"Replay map dimensions are unsupported.");dimensions=true;
  }else if(!memcmp(tag,"MTXM",4))terrain=size;
  else if(!memcmp(tag,"STRx",4))validate_strings(data,size,4);
  else if(!memcmp(tag,"STR ",4))validate_strings(data,size,2);
  else if(!memcmp(tag,"OWNR",4)||!memcmp(tag,"SIDE",4)) {
   need(size>=12,"Truncated replay player slots.");
   for(int i=0;i<12;i++)need(data[i]<=8,"Invalid replay map player slots.");
  }

  at+=size;
 }
 need(dimensions&&terrain>=size_t(width)*height*2,"Replay map terrain is missing.");
}
static Parsed parse(const uint8_t* data,size_t size) {
 need(data&&size>=40&&size<=MAX_FILE,"Replay exceeds 32 MB or is empty.");
 auto base=bwgame::data_loading::data_reader_le(data,data+size);
 auto reader=bwgame::data_loading::make_replay_file_reader(base);Parsed p;
 need(reader.get<uint32_t>()==0x53526572,"Unsupported replay format. Choose a classic .rep file.");
 p.headerStart=base.tell();reader.get_bytes(p.header.data(),p.header.size());p.headerEnd=base.tell();auto* h=p.header.data();
 p.expansion=h[0]!=0;p.frames=int(u32(h+1));
 need(h[0]<=1&&p.frames>0&&p.frames<=24*60*60*24,"Replay duration is invalid.");
 p.title=clean(h+72,25);p.mapName=clean(h+97,32);
 unsigned gameType=unsigned(h[60])|(unsigned(h[61])<<8);
 need(gameType==2||gameType==3,"Only classic melee and free-for-all replays are supported.");
 // Header slots start at byte 161; player id/controller/race/force/name are fixed-size.
 for(int i=0;i<12;i++) {size_t at=161+i*36;p.controllers[i]=h[at+8];p.races[i]=h[at+9];p.names[i]=clean(h+at+11,25);
  need(p.controllers[i]<=8&&p.races[i]<=7,"Invalid replay player slots.");}
 for(int i=0;i<8;i++)need(u32(h+593+i*4)<16,"Invalid replay player colors.");
 auto block=[&](bwgame::a_vector<uint8_t>& out,size_t maximum){size_t n=reader.get<uint32_t>();need(n<=maximum,"Replay data is too large.");out.resize(n);reader.get_bytes(out.data(),n);};
 block(p.actions,MAX_ACTIONS);block(p.map,MAX_MAP);need(p.map.size()>=16,"Replay map is missing.");p.classicSize=base.tell();
 // Reject invalid action frame boundaries before any game is replaced.
 int previous=-1;for(size_t at=0;at<p.actions.size();) {
  need(p.actions.size()-at>=5,"Truncated replay action frame.");int frame=int(u32(p.actions.data()+at));size_t n=p.actions[at+4];at+=5;
  need(frame>=previous&&frame<=p.frames&&n<=p.actions.size()-at,"Invalid replay action timeline.");previous=frame;at+=n;
 }
 validate_map(p);validate_actions(p);
 if(base.left()) {
  need(base.get<uint64_t>()==MAGIC,"Unsupported replay extension or Remastered replay.");
  auto engine=base.get<uint32_t>();
  need(engine==ENGINE,"The selected replay was recorded with a different version of the app.");
  auto& r=p.rules;r.present=true;r.signature=base.get<uint64_t>();r.initial=base.get<uint64_t>();r.ai=base.get<uint32_t>();
  r.difficulty=base.get<uint8_t>();r.player=base.get<uint8_t>();auto boolean=[&](){unsigned v=base.get<uint8_t>();need(v<=1,"Invalid replay flags.");return v!=0;};r.heroes=boolean();r.teams=boolean();r.defenders=boolean();
  need(r.ai<256&&r.difficulty<=2&&r.player<8,"Invalid replay setup.");
  unsigned ai=0;for(int i=0;i<8;i++)if(p.controllers[i]==bwgame::player_t::controller_computer_game)ai|=1u<<i;
  need(ai==r.ai&&p.controllers[r.player]==bwgame::player_t::controller_occupied,"Replay player setup does not match its header.");
  for(auto& row:r.alliances)for(auto& v:row){v=base.get<uint8_t>();need(v<=1,"Invalid replay alliances.");}
  for(auto& v:r.vision){v=base.get<uint32_t>();need(v<4096,"Invalid replay vision.");}
  need(base.left()==8,"Unexpected replay data.");auto expected=base.get<uint64_t>();
  need(expected==native_save::hash_bytes(data,size-8),"Replay is damaged.");
 } else for(int i=0;i<8;i++)need(p.controllers[i]!=bwgame::player_t::controller_computer_game&&p.controllers[i]!=bwgame::player_t::controller_computer,"This computer-player replay lacks StarDroid AI settings. Record a new replay.");
 return p;
}
static void reset(){recording=Rules();playing=Parsed();std::vector<uint8_t>().swap(opened);active=false;viewpoint=-1;
 checkpoints.clear();seeking=false;seek_target=0;checkpoint_disabled=false;replay_seek_audio_muted=false;}
static void begin() {
 recording=Rules();if(!g_ui||!g_ui->rep_on||g_ui->st.current_frame!=0)return;
 auto& r=recording;r.present=true;r.player=g_ui->my_player;r.heroes=g_ui->game_st.hero_mode;r.teams=g_ui->game_st.team_game;r.difficulty=g_campaign?g_campaign->melee_ai_level:2;
 for(int i=0;i<8;i++)if(g_ui->st.players[i].controller==bwgame::player_t::controller_computer_game)r.ai|=1u<<i;
 for(int i=0;i<12;i++){r.vision[i]=g_ui->st.shared_vision[i];for(int j=0;j<12;j++)r.alliances[i][j]=g_ui->st.alliances[i][j];}
 r.defenders=g_campaign?g_campaign->base_defence_enabled:true;r.signature=native_save::assets_signature(*g_ui,g_campaign);r.initial=bwgame::sim_checksum(g_ui->st);
}
static std::vector<uint8_t> save() {
 if(active||!g_ui||!g_ui->rep_on||!recording.present)return {};
 const auto* data=openbw_save_replay();if(!data)return {};
 size_t n=openbw_replay_len();need(n<MAX_FILE-256,"Replay is too large.");std::vector<uint8_t> out(data,data+n);out.reserve(n+256);
 auto w=bwgame::data_loading::make_vector_writer(out);auto& r=recording;
 w.put<uint64_t>(MAGIC);w.put<uint32_t>(ENGINE);w.put<uint64_t>(r.signature);w.put<uint64_t>(r.initial);w.put<uint32_t>(r.ai);
 w.put<uint8_t>(r.difficulty);w.put<uint8_t>(r.player);w.put<uint8_t>(r.heroes);w.put<uint8_t>(r.teams);w.put<uint8_t>(r.defenders);
 for(auto& row:r.alliances)for(auto v:row)w.put<uint8_t>(v);for(auto v:r.vision)w.put<uint32_t>(v);
 w.put<uint64_t>(native_save::hash_bytes(out.data(),out.size()));return out;
}
static std::string info(const uint8_t* data,size_t size) {
 auto p=parse(data,size);std::string s=std::to_string(p.frames)+"\t"+(p.expansion?"1":"0")+"\t"+(p.rules.present?"1":"0")+"\t"+p.mapName+"\t"+p.title;
 for(int i=0;i<8;i++)if(p.controllers[i]==bwgame::player_t::controller_occupied||p.controllers[i]==bwgame::player_t::controller_computer_game)s+="\n"+std::to_string(i)+"\t"+std::to_string(p.races[i])+"\t"+p.names[i];return s;
}
static void set_view(int owner) {
 if(!active||!g_ui)return;if(owner<0||owner>7)owner=-1;
 viewpoint=owner;g_ui->fog_player=owner;g_ui->current_selection_clear();g_ui->fog_snapshots.assign(2500,{});
 if(owner>=0)g_ui->center_on(g_ui->game_st.start_locations[owner]);
}
static const char* resources() {
 static std::string text;int owner=viewpoint>=0?viewpoint:g_ui->my_player;
 int race=std::min(2,playing.races[owner]);
 if(viewpoint<0&&!g_ui->current_selection.empty())if(auto* u=g_ui->get_unit(g_ui->current_selection.front());u&&u->owner<8){owner=u->owner;race=int(g_ui->unit_race(u));}
 if(race<0||race>2)race=1;auto& st=g_ui->st;
 text=std::to_string(int(st.current_minerals[owner]))+"\t"+std::to_string(int(st.current_gas[owner]))+"\t"+std::to_string(st.supply_used[owner][race].raw_value/2)+"\t"+std::to_string(std::min<int>(200,st.supply_available[owner][race].raw_value/2));
 int activeRaces=1<<race;for(auto* u:bwgame::ptr(st.player_units[owner]))if(!g_ui->unit_dying(u)&&!g_ui->ut_turret(u)){int r=int(g_ui->unit_race(u));if(r>=0&&r<3)activeRaces|=1<<r;}
 text+="\t"+std::to_string(activeRaces);for(int r=0;r<3;r++)text+="\t"+std::to_string(st.supply_used[owner][r].raw_value/2)+"\t"+std::to_string(std::min<int>(200,st.supply_available[owner][r].raw_value/2));
 return text.c_str();
}
static void open(const uint8_t* data,size_t size) {
 auto parsed=parse(data,size);std::vector<uint8_t> owned(data,data+size);close_game();
 try {
  playing=std::move(parsed);opened=std::move(owned);auto& p=playing;auto& r=p.rules;g_skirmish_expansion=p.expansion;
  g_loader=new loader_t(bwgame::data_loading::data_files_directory<loader_t>(""));bwgame::game_player player(*g_loader);
  int owner=r.present?r.player:0;for(int i=0;!r.present&&i<8;i++)if(p.controllers[i]==bwgame::player_t::controller_occupied){owner=i;break;}
  auto race=bwgame::race_t(std::min(2,p.races[owner]));
  if(r.ai){g_campaign=new bwgame::campaign_ui(std::move(player),owner,race);g_ui=g_campaign;}else g_ui=new bwgame::play_ui(std::move(player),owner,race);
  g_ui->exit_on_close=false;g_ui->load_data_file=[](bwgame::a_vector<uint8_t>& b,bwgame::a_string name){(*g_loader)(b,std::move(name));};g_ui->init();
  bwgame::replay_functions load(g_ui->st,g_ui->current_action_state,g_ui->current_replay_state);
  // StarDroid creates all melee starting units as occupied slots, then starts
  // computer scripts. Creating them as computers changes initial order timers.
  // Rewrite only the small header used for loading; the saved file is untouched.
  if(r.present&&r.ai){
   auto header=p.header;for(int i=0;i<8;i++)if(r.ai&(1u<<i))header[161+i*36+8]=bwgame::player_t::controller_occupied;
   std::vector<uint8_t> loadBytes(opened.begin(),opened.begin()+p.headerStart);loadBytes.reserve(p.classicSize+1024);auto writer=bwgame::data_loading::make_vector_writer(loadBytes);
   auto replayWriter=bwgame::data_loading::make_replay_file_writer(writer);replayWriter.put_bytes(header.data(),header.size());
   loadBytes.insert(loadBytes.end(),opened.begin()+p.headerEnd,opened.begin()+p.classicSize);load.load_replay_data(loadBytes.data(),loadBytes.size());
   for(int i=0;i<8;i++)if(r.ai&(1u<<i))g_ui->st.players[i].controller=bwgame::player_t::controller_computer_game;
  }else load.load_replay_data(opened.data(),p.classicSize);
  g_ui->rep_map=p.map;
  if(!p.expansion){bwgame::game_load_functions rules(g_ui->st);for(int slot=0;slot<12;slot++){
    for(const auto& type:g_ui->game_st.unit_types.vec)if(type.is_broodwar)rules.game_st.unit_type_allowed[slot][type.id]=false;
    for(const auto& type:g_ui->game_st.upgrade_types.vec)if(type.is_broodwar){rules.game_st.max_upgrade_levels[slot][type.id]=0;g_ui->st.upgrade_levels[slot][type.id]=0;}
    for(int i=24;i<44;i++){rules.game_st.tech_available[slot][(bwgame::TechTypes)i]=false;g_ui->st.tech_researched[slot][(bwgame::TechTypes)i]=false;}
  }}
  if(g_campaign){
   const char* names[]={"scripts/aiscript.bin","scripts/bwscript.bin"};for(int i=0;i<2;i++)for(auto& a:g_loader->mpqs)if(a.mpq.file_exists(names[i])){a(g_campaign->scripts[i],names[i]);break;}
   g_campaign->melee_ai=true;g_campaign->melee_ai_level=r.difficulty;g_campaign->base_defence_enabled=r.defenders;
   const uint32_t ids[3][3]={{0x784f4c5au,0x78454d5au,0x7849485au},{0x784f4c54u,0x78454d54u,0x78494854u},{0x784f4c50u,0x78454d50u,0x78494850u}};
   for(int i=0;i<8;i++)if(r.ai&(1u<<i)){auto id=ids[std::min(2,p.races[i])][r.difficulty];if(!p.expansion)id=(id&0x00ffffffu)|((r.difficulty==1?uint32_t('D'):uint32_t('f'))<<24);g_campaign->ai_start(i,id,0);}
   need(g_campaign->ai_error.empty(),"Replay AI scripts are unavailable.");
  }
  if(r.present){configure_hero_mode(r.heroes);bwgame::game_load_functions rules(g_ui->st);rules.game_st.team_game=r.teams;
   for(int i=0;i<12;i++){g_ui->st.shared_vision[i]=r.vision[i];for(int j=0;j<12;j++)g_ui->st.alliances[i][j]=r.alliances[i][j];}
   need(native_save::assets_signature(*g_ui,g_campaign)==r.signature,"Replay needs the same game data used to record it.");
   need(uint64_t(bwgame::sim_checksum(g_ui->st))==r.initial,"Replay initial state does not match this build.");
  }
  g_ui->rep_on=false;g_ui->spectator=true;g_ui->paused_replay_inspection=true;g_ui->competitive=false;g_ui->outcome=0;g_ui->wnd.create("StarDroid Replay",0,0,640,480);g_ui->resize(640,480);g_ui->set_image_data();
  g_ui->center_on(g_ui->game_st.start_locations[owner]);g_ui->outgoing.clear();active=true;set_view(-1);openbw_set_external_cursor(1);openbw_mouse_move(-100,-100);remember_checkpoint();
 }catch(...){close_game();throw;}
}
static void advance() {
 if(!active||!g_ui||g_ui->st.current_frame>=playing.frames)return;
 g_ui->outgoing.clear();g_ui->outcome=0;
 // Execute only recorded player input; continue the same local AI simulation.
 g_ui->execute_actions(playing.actions.data(),playing.actions.data()+playing.actions.size());
 if(g_campaign)g_campaign->campaign_tick();g_ui->state_functions::next_frame();g_ui->outgoing.clear();g_ui->outcome=0;
}
// A snapshot operation is bounded by one world, unlike replaying a long match.
// Fixed byte/count caps prevent a long replay from retaining unbounded worlds.
static void remember_checkpoint() {
 if(!active||!g_ui||checkpoint_disabled)return;
 int frame=g_ui->st.current_frame;
 for(auto& c:checkpoints)if(std::abs(c.frame-frame)<CHECKPOINT_INTERVAL)return;
 try {
  native_save::HostState host;auto bytes=native_save::save(*g_ui,g_campaign,host);
  if(bytes.capacity()>MAX_CHECKPOINT_BYTES){checkpoint_disabled=true;return;}
  // Reserve room before installing the new checkpoint. Always retain frame zero.
  while(checkpoints.size()>=MAX_CHECKPOINTS||cache_bytes()+bytes.capacity()>MAX_CHECKPOINT_BYTES){
   if(checkpoints.size()<=1)return;
   size_t remove=1;int tightest=std::numeric_limits<int>::max();
   for(size_t i=1;i<checkpoints.size();i++){
    int next=i+1<checkpoints.size()?checkpoints[i+1].frame:frame;
    int gap=std::abs(next-checkpoints[i-1].frame);
    if(gap<tightest){tightest=gap;remove=i;}
   }
   checkpoints.erase(checkpoints.begin()+remove);
  }
  checkpoints.push_back({frame,std::move(bytes)});
  std::sort(checkpoints.begin(),checkpoints.end(),[](const Checkpoint& a,const Checkpoint& b){return a.frame<b.frame;});
 }catch(const std::exception&){
  // A large or unsupported snapshot must not make a valid replay unplayable.
  // Backward seeks can always reload its original, already-validated bytes.
  checkpoint_disabled=true;
 }
}
static void silence_seek_audio(){
 for(int ch=0;ch<57;ch++)naudio::stop(ch);naudio::stop(62);
 if(g_ui)for(auto& c:g_ui->sound_channels)c.playing=false;
}
static void clear_seek_presentation(){
 if(!g_ui)return;
 g_ui->current_selection_clear();g_ui->fog_snapshots.assign(2500,{});
 g_ui->outgoing.clear();g_ui->outcome=0;g_ui->rep_on=false;
 g_ui->events_seeded=false;g_ui->last_life.clear();g_ui->last_stim.clear();g_ui->announced.clear();
 g_ui->alerts.clear();g_ui->advisor_queue.clear();g_ui->ready_notifications=0;g_ui->technology_notifications=0;
 g_ui->rescue_notifications=0;g_ui->msg_log.clear();
 silence_seek_audio();
}
// Queue only: touch events never run catch-up work or reload the world.
static void seek(int target){
 if(!active||!g_ui)return;
 seek_target=std::max(0,std::min(playing.frames,target));
 seeking=seek_target!=g_ui->st.current_frame;
 replay_seek_audio_muted=seeking;if(seeking)silence_seek_audio();else clear_seek_presentation();
}
static void cancel_seek(){
 if(seeking)clear_seek_presentation();seeking=false;replay_seek_audio_muted=false;
 seek_target=g_ui?g_ui->st.current_frame:0;
}
static void pump_seek(){
 if(!seeking||!active||!g_ui)return;
 int target=seek_target;
 auto deadline=std::chrono::steady_clock::now()+std::chrono::milliseconds(6);
 Checkpoint* best=nullptr;
 for(auto& c:checkpoints)if(c.frame<=target&&(!best||c.frame>best->frame))best=&c;
 bool restore=g_ui->st.current_frame>target||(best&&best->frame>g_ui->st.current_frame);
 if(restore){
  auto camera=g_ui->screen_pos;int owner=viewpoint;bool paused=g_ui->paused;
  int width=g_ui->screen_width,height=g_ui->screen_height,inset=g_ui->hud_bottom_inset;
  auto scale=g_ui->minimap_scale;int volume=g_ui->global_volume;
  bool terrain=g_ui->minimap_terrain,lines=g_ui->show_order_lines,edge=g_ui->host_controls_edge_scroll;
  bool larvae=g_ui->original_larvae_selection,single=g_ui->single_larva_morph;
  if(best)native_save::restore(*g_ui,g_campaign,best->bytes);
  else {
   // No retained anchor (for example, an oversized map): reload one world,
   // then resume bounded work next tick. open() owns its input before reset().
   open(opened.data(),opened.size());
   openbw_resize(width,height);openbw_set_view_scale(scale);g_ui->hud_bottom_inset=inset;
   g_ui->global_volume=volume;g_ui->minimap_terrain=terrain;g_ui->show_order_lines=lines;
   g_ui->host_controls_edge_scroll=edge;g_ui->original_larvae_selection=larvae;g_ui->single_larva_morph=single;
  }
  viewpoint=owner;g_ui->fog_player=owner;g_ui->screen_pos=camera;g_ui->clamp_viewport();g_ui->paused=paused;
  seeking=true;seek_target=target;replay_seek_audio_muted=true;clear_seek_presentation();
  // Return immediately after the world-sized restore/reload operation.
  if(g_ui->st.current_frame==target)cancel_seek();
  return;
 }
 auto volume=g_ui->global_volume;g_ui->global_volume=0;
 try {
  for(int count=0;count<SEEK_BATCH_FRAMES&&g_ui->st.current_frame<target;count++){
   advance();
   if(std::chrono::steady_clock::now()>=deadline)break;
  }
 }catch(...){g_ui->global_volume=volume;cancel_seek();throw;}
 g_ui->global_volume=volume;
 if(g_ui->st.current_frame==target){remember_checkpoint();cancel_seek();}
 else if(g_ui->st.current_frame%CHECKPOINT_INTERVAL<SEEK_BATCH_FRAMES)remember_checkpoint();
}
static void step(){
 if(!active||!g_ui||g_ui->paused||seeking)return;
 advance();
 if(g_ui->st.current_frame%CHECKPOINT_INTERVAL==0)remember_checkpoint();
}
} // namespace native_replay
