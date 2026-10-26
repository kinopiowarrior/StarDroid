#!/usr/bin/env python3
"""Run the real ai_detect implementation against small deterministic fixtures.

No copyrighted game assets are needed. The fixture substitutes engine queries and
order dispatch, not the selection method under test. Pass --cxx for a host compiler.
A Windows NDK clang works with the freestanding host-link mode below.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PRELUDE = r'''
using uint32_t = unsigned;
extern "C" void* memcpy(void* d,const void* s,unsigned long long n){auto* a=(char*)d;auto* b=(const char*)s;for(unsigned long long i=0;i<n;++i)a[i]=b[i];return d;}
extern "C" void* memset(void* d,int v,unsigned long long n){auto* a=(char*)d;for(unsigned long long i=0;i<n;++i)a[i]=(char)v;return d;}
namespace std { template<class T> T max(T a,T b){return a>b?a:b;} }
struct xy {int x=0,y=0;};
xy operator-(xy a,xy b){return {a.x-b.x,a.y-b.y};}
int abs11(int v){return v<0?-v:v;}
int xy_length(xy p){return abs11(p.x)+abs11(p.y);}
struct fp8 {static int integer(int v){return v;}};
enum class UnitTypes {Other,Terran_Science_Vessel,Protoss_Observer,Zerg_Overlord,Spell_Scanner_Sweep,Terran_Comsat_Station};
enum class Orders {Guard,Follow,Move,CastScannerSweep};
struct TechTypes {enum {Scanner_Sweep};};
struct player_t {enum {controller_computer_game=5};int controller=5;};
struct order_type_t {Orders id=Orders::Guard;};
order_type_t order_types[]={{Orders::Guard},{Orders::Follow},{Orders::Move},{Orders::CastScannerSweep}};
struct unit_t {
 int owner=1,index=0,energy=200,sight=288,order_state=0;xy position{},move_target{};UnitTypes type=UnitTypes::Other;
 order_type_t* order_type=&order_types[0];struct {xy pos;unit_t* unit=nullptr;} order_target{};
 bool dead=false,completed=true,disabled=false,blinded=false,hallucinated=false,loaded=false,hidden=false;
 bool locked=false,nobrk=false,lifting=false,cargo=false;unsigned detected=0,live_detected=0;
};
struct unit_list {unit_t* list[32]{};int n=0;unit_t** begin(){return list;}unit_t** end(){return list+n;}};
unit_list& ptr(unit_list& a){return a;}
struct state {player_t players[8];unit_list player_units[8];unit_t* selection=nullptr;int orders=0;};
struct action_functions {
 state& st;action_functions(state& s,int&):st(s){}
 void action_select(int,unit_t* u){st.selection=u;}
 bool action_order(int,order_type_t* o,xy pos,unit_t* target,void*,bool){
  st.selection->order_type=o;st.selection->order_state=0;st.selection->order_target.pos=target?target->position:pos;st.selection->order_target.unit=target;++st.orders;return true;
 }
};
struct campaign_ai {
 state st;int ai_actions=0,my_player=0;struct {bool active=true;} towns[8];
 struct {bool tech_available[8][1]{};} game_st;
 bool unit_dying(unit_t* u){return u->dead;}
 bool unit_target_is_enemy(unit_t* a,unit_t* b){return a->owner!=b->owner;}
 bool unit_is_undetected(unit_t* u,int p){return !(u->detected&(1u<<p));}
 unsigned unit_calculate_detected_flags(unit_t* u){return u->live_detected;}
 bool unit_is(unit_t* u,UnitTypes type){return u->type==type;}
 bool unit_can_detect(unit_t* u){return u->completed&&!u->disabled&&!u->blinded;}
 bool u_hallucination(unit_t* u){return u->hallucinated;}
 bool u_loaded(unit_t* u){return u->loaded;}
 bool us_hidden(unit_t* u){return u->hidden;}
 bool u_order_not_interruptible(unit_t* u){return u->locked;}
 bool u_iscript_nobrk(unit_t* u){return u->nobrk;}
 bool ai_lifting(int,unit_t* u){return u->lifting;}
 struct cargo_list {bool cargo;bool empty(){return !cargo;}};
 cargo_list loaded_units(unit_t* u){return {u->cargo};}
 int units_distance(unit_t* a,unit_t* b){return xy_length(a->position-b->position);}
 bool u_completed(unit_t* u){return u->completed;}
 bool unit_is_disabled(unit_t* u){return u->disabled;}
 bool ai_idle(unit_t* u){return u->order_type->id==Orders::Guard;}
 int unit_sight_range(unit_t* u){return u->sight;}
 order_type_t* get_order_type(Orders o){return &order_types[int(o)];}
 void move_to_target_reset(unit_t* u,unit_t* target){u->move_target=target->position;}
 void set_unit_move_target(unit_t* u,xy p){u->move_target=p;}
 void set_next_target_waypoint(unit_t*,xy){}
 bool unit_is_at_move_target(unit_t* u){return u->position.x==u->move_target.x&&u->position.y==u->move_target.y;}
 void order_done(unit_t* u){u->order_type=&order_types[0];u->order_target.unit=nullptr;}
'''
POSTLUDE = r'''
};
campaign_ai ai;
unit_t units[24];
void reset(){
 ai.st.orders=0;ai.st.player_units[1].n=0;ai.my_player=0;ai.towns[1].active=true;
 ai.st.players[1].controller=5;ai.game_st.tech_available[1][0]=false;
 for(int i=0;i<24;++i){
  auto& u=units[i];u.owner=1;u.index=i;u.energy=200;u.sight=288;u.order_state=0;u.move_target={0,0};u.position={0,0};u.type=UnitTypes::Other;
  u.order_type=&order_types[0];u.order_target.pos={0,0};u.order_target.unit=nullptr;
  u.dead=false;u.completed=true;u.disabled=false;u.blinded=false;u.hallucinated=false;
  u.loaded=false;u.hidden=false;u.locked=false;u.nobrk=false;u.lifting=false;u.cargo=false;u.detected=0;u.live_detected=0;
 }
 units[0].owner=0;units[0].position={1200,0};units[1].position={1100,0};
}
unit_t* detector(int i,int x,UnitTypes type=UnitTypes::Protoss_Observer){
 auto& u=units[i];u.type=type;u.position={x,0};auto& a=ai.st.player_units[1];a.list[a.n++]=&u;return &u;
}
bool moved(int i){return units[i].order_type->id==Orders::Move;}
void hit(){ai.ai_detect(&units[1],&units[0]);}
int main(){
 // Repeated damage used to pull the whole detector pool. Test all three races.
 for(int r=0;r<3;++r){
  reset();auto type=r==0?UnitTypes::Terran_Science_Vessel:r==1?UnitTypes::Protoss_Observer:UnitTypes::Zerg_Overlord;
  detector(2,300,type);detector(3,800,type);detector(4,600,type);
  for(int i=0;i<30;++i)hit();
  if(!moved(3)||moved(2)||moved(4)||ai.st.orders!=1)return 1+r;
  // A simultaneous distant incident can use another detector.
  units[5].owner=0;units[5].position={2300,0};units[6].position={2200,0};detector(7,1900,type);
  for(int i=0;i<30;++i)ai.ai_detect(&units[6],&units[5]);
  if(!moved(7)||ai.st.orders!=2)return 4+r;
  // A killed responder frees the reservation for the next nearest detector.
  units[3].dead=true;hit();if(!moved(4)||ai.st.orders!=3)return 7+r;
 }
 // A completed response can be reused for a later nearby incident.
 reset();detector(2,800);hit();units[2].position={1200,0};units[2].order_type=&order_types[0];
 units[0].position={1700,0};units[1].position={1600,0};detector(3,900);hit();
 if(!moved(2)||moved(3)||ai.st.orders!=2||units[2].order_target.pos.x!=1700)return 10;
 // Preserve scripted travel; unavailable units never reserve or answer a call.
 reset();for(int i=2;i<12;++i)detector(i,1100-(i-2)*10,UnitTypes::Zerg_Overlord);
 units[2].blinded=true;units[3].hallucinated=true;units[4].cargo=true;units[5].lifting=true;
 units[6].disabled=true;units[7].loaded=true;units[8].hidden=true;units[9].locked=true;
 units[10].order_type=&order_types[2];units[10].order_target.pos={3000,0};
 hit();if(!moved(11)||ai.st.orders!=1||units[10].order_target.pos.x!=3000)return 11;
 // A blind/cargo transport already moving to the area is not a reservation.
 reset();detector(2,800,UnitTypes::Zerg_Overlord);units[2].cargo=true;units[2].order_type=&order_types[2];units[2].order_target.pos={1200,0};
 detector(3,600);hit();if(!moved(3)||ai.st.orders!=1)return 12;
 // Pending Comsat cast counts before the scanner spell unit exists.
 reset();ai.game_st.tech_available[1][0]=true;detector(2,300,UnitTypes::Terran_Comsat_Station);detector(3,600,UnitTypes::Terran_Comsat_Station);
 detector(4,800);for(int i=0;i<30;++i)hit();if(ai.st.orders!=1||units[2].order_type->id!=Orders::CastScannerSweep||moved(4))return 13;
 // Real current detection suppresses dispatch while cached flags catch up.
 reset();detector(2,800);units[0].live_detected=2;hit();if(ai.st.orders)return 14;
 // Explicit nearest-to-attacker selection and stable tie break.
 reset();detector(4,1600);detector(2,800);units[1].position={1700,0};hit();if(!moved(2)||moved(4))return 15;
 // Escorts can respond, remote detectors stay home, inactive/human AI is ignored.
 reset();detector(2,800);units[2].order_type=&order_types[1];detector(3,600);hit();if(!moved(2)||moved(3))return 16;
 reset();detector(2,2600);hit();if(ai.st.orders)return 17;
 reset();detector(2,800);ai.towns[1].active=false;hit();if(ai.st.orders)return 18;
 ai.towns[1].active=true;ai.my_player=1;hit();if(ai.st.orders)return 19;
 ai.my_player=0;ai.st.players[1].controller=0;hit();if(ai.st.orders)return 20;
 // Existing move reservations need no unsaved side state: a fresh AI instance
 // given the same game orders sees the same reservation.
 reset();detector(2,800);hit();campaign_ai restored;restored.st=ai.st;restored.ai_detect(&units[1],&units[0]);
 if(restored.st.orders!=1)return 21;
 // A moving attacker must refresh the first responder, not exhaust the pool.
 reset();detector(2,800,UnitTypes::Zerg_Overlord);detector(3,600,UnitTypes::Zerg_Overlord);detector(4,400,UnitTypes::Zerg_Overlord);hit();
 for(int step=0;step<8;++step){units[0].position.x+=96;units[1].position.x+=96;hit();}
 if(!moved(2)||moved(3)||moved(4)||units[2].order_target.unit!=&units[0]||units[2].order_target.pos.x!=units[0].position.x)return 22;
 // A remote unrelated scripted move cannot block a closer response.
 reset();detector(2,3000);units[2].order_type=&order_types[2];units[2].order_target.pos={1200,0};detector(3,800);hit();
 if(!moved(3)||ai.st.orders!=1)return 23;
 // Run the real engine Move order: after its initial observed destination is
 // captured, an unseen source moving without a new attack does not retarget it.
 reset();detector(2,800);hit();ai.order_Move(&units[2]);
 units[0].position={1600,0};ai.order_Move(&units[2]);
 if(units[2].move_target.x!=1200||units[2].order_state!=1)return 24;
 hit();ai.order_Move(&units[2]);if(units[2].move_target.x!=1600||ai.st.orders!=2)return 25;
 // Different Dark Templars in one attack area share its responder too.
 reset();detector(2,800);detector(3,600);detector(4,400);hit();
 for(int i=5;i<13;++i){units[i].owner=0;units[i].position={1160+(i-5)*12,0};}
 for(int round=0;round<12;++round)for(int i=5;i<13;++i)ai.ai_detect(&units[1],&units[i]);
 if(ai.st.orders!=1||!moved(2)||moved(3)||moved(4))return 26;
 return 0;
}
'''

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--cxx',default=os.environ.get('CXX'))
    parser.add_argument('--windows-ndk',action='store_true')
    args=parser.parse_args()
    source=(ROOT/'native-engine/web/campaign_ai.h').read_text(encoding='utf-8')
    start=source.index('    void ai_detect(unit_t* victim,unit_t* source) {')
    end=source.index('    void on_scenario_attack',start)
    out=ROOT/'build-native/detector-unit';out.mkdir(parents=True,exist_ok=True)
    engine=(ROOT/'native-engine/bwgame.h').read_text(encoding='utf-8')
    move_start=engine.index('\tvoid order_Move(unit_t* u) {')
    move_end=engine.index('\n\tvoid order_Nothing',move_start)
    cpp=out/'detectors.cpp';cpp.write_text(PRELUDE+source[start:end]+engine[move_start:move_end]+POSTLUDE,encoding='utf-8')
    compiler=args.cxx or shutil.which('clang++') or shutil.which('g++')
    if not compiler:raise SystemExit('No host C++ compiler found; fixture generated at '+str(cpp))
    exe=out/('detectors.exe' if os.name=='nt' else 'detectors')
    flags=['-std=c++17','-O0']
    if args.windows_ndk:
        # This fixture deliberately needs no platform headers or C/C++ runtime.
        # NDK clang can therefore test it on Windows without a separate SDK.
        linker=Path(compiler).with_name('lld-link.exe')
        if not linker.exists():
            linker=out/'lld-link.exe';shutil.copyfile(Path(compiler).with_name('lld.exe'),linker)
        flags+=['--target=x86_64-pc-windows-msvc','--ld-path='+str(linker),'-nostdlib','-fno-builtin','-fno-stack-protector','-fno-exceptions','-fno-rtti','-Wl,/entry:main,/subsystem:console']
    subprocess.run([compiler,*flags,str(cpp),'-o',str(exe)],check=True)
    result=subprocess.run([str(exe)])
    if result.returncode:raise SystemExit('Detector behavior assertion failed: '+str(result.returncode))
    print('PASS detector responses: three races; repeat-hit cap; nearest/tie; two sites; grouped attackers; moving attacker; no passive target chase; reuse/loss; scripted travel; eligibility; scan deduplication; coverage; restore orders')

if __name__=='__main__':main()
