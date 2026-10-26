#!/usr/bin/env python3
"""Exercise the production SuiR opcode and targeting policy without game assets.

Dunes of Shakuras (expprotoss02) runs SuiR twice. Stock SuiR invokes
send_suicide(1), whereas strategic Suic invokes send_suicide(0). This fixture
substitutes engine queries and order dispatch, but extracts the implementation
and opcode dispatch under test from the prepared campaign_ai.h.
It is not a substitute for a full mission playthrough with original MPQ data.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PRELUDE = r'''
extern "C" void* memcpy(void* d,const void* s,unsigned long long n){auto* a=(char*)d;auto* b=(const char*)s;for(unsigned long long i=0;i<n;++i)a[i]=b[i];return d;}
extern "C" void* memset(void* d,int v,unsigned long long n){auto* a=(char*)d;for(unsigned long long i=0;i<n;++i)a[i]=(char)v;return d;}
struct xy {int x=0,y=0;};
struct order_type_t {int id;};
order_type_t idle{0}, attack{1};
struct unit_type_t {order_type_t* attack_unit=&attack;};
unit_type_t normal_type;
struct sprite_t {xy position;};
struct unit_t {
 int owner=0; sprite_t* sprite=nullptr; unit_type_t* unit_type=&normal_type;
 order_type_t* order_type=&idle;
 struct target_t {xy pos;unit_t* unit=nullptr;} order_target{};
 bool dead=false,completed=true,building=false,turret=false,loaded=false,hidden=false;
 bool fighter=false,disabled=false,locked=false,nobrk=false,casting=false;
 bool invincible=false,undetected=false,flying=false,ground_weapon=true,air_weapon=false;
 bool worker=false,hero=false,previous_attacker=false;
};
struct unit_list {unit_t* list[32]{};int n=0;unit_t** begin(){return list;}unit_t** end(){return list+n;}};
unit_list& ptr(unit_list& a){return a;}
struct campaign_ai {
 struct {unit_list player_units[8];bool alliances[8][8]{};} st;
 struct {xy center{};int orders=0;} towns[8];
 struct thread {int owner=0,operand=0;xy pos;};
 int strategic_calls=0,read_count=0;
 bool unit_dying(unit_t* u){return u->dead;}
 bool u_completed(unit_t* u){return u->completed;}
 bool ut_building(unit_t* u){return u->building;}
 bool ut_turret(unit_t* u){return u->turret;}
 bool u_loaded(unit_t* u){return u->loaded;}
 bool us_hidden(unit_t* u){return u->hidden;}
 bool unit_is_fighter(unit_t* u){return u->fighter;}
 bool unit_is_disabled(unit_t* u){return u->disabled;}
 bool u_order_not_interruptible(unit_t* u){return u->locked;}
 bool u_iscript_nobrk(unit_t* u){return u->nobrk;}
 bool ai_casting(unit_t* u){return u->casting;}
 bool unit_can_attack_target(unit_t* u,unit_t* other){
  return !other->invincible&&!other->hidden&&!other->undetected && (other->flying?u->air_weapon:u->ground_weapon);
 }
 bool is_reachable(unit_t*,xy pos){return pos.y!=999;}
 int units_distance(unit_t* a,unit_t* b){int x=a->sprite->position.x-b->sprite->position.x;int y=a->sprite->position.y-b->sprite->position.y;return (x<0?-x:x)+(y<0?-y:y);}
 void issue_order(unit_t* u,bool,order_type_t* order,unit_t::target_t target){u->order_type=order;u->order_target=target;}
 int ai_read(thread& t,int n){read_count+=n;return t.operand;}
 void ai_attack(int,bool){++strategic_calls;}
'''
POSTLUDE = r'''
};
campaign_ai ai;
unit_t units[24];sprite_t sprites[24];
void reset(){
 ai.strategic_calls=0;ai.read_count=0;
 for(int p=0;p<8;++p){ai.st.player_units[p].n=0;ai.towns[p].orders=0;ai.towns[p].center={0,0};for(int q=0;q<8;++q)ai.st.alliances[p][q]=false;}
 for(int i=0;i<24;++i){units[i]=unit_t{};sprites[i].position={0,0};units[i].sprite=&sprites[i];}
}
unit_t* add(int i,int owner,int x,int y=0){units[i].owner=owner;sprites[i].position={x,y};auto& list=ai.st.player_units[owner];list.list[list.n++]=&units[i];return &units[i];}
void run(int mode=1){campaign_ai::thread t;t.owner=0;t.operand=mode;t.pos={42,84};ai.run_opcode31(t);}
int main(){
 // Two DTs independently choose the nearby hydralisk rather than the distant
 // enemy base; an old town-wave attacker remains eligible for SuiR.
 reset();add(0,0,0);add(1,0,900);add(2,1,100);add(3,1,800);add(4,1,3000)->building=true;
 units[0].previous_attacker=true;run();
 if(units[0].order_target.unit!=&units[2]||units[1].order_target.unit!=&units[3]||ai.towns[0].orders!=2||ai.strategic_calls||ai.read_count!=1)return 1;
 // Re-running a preserved trigger does not restart the current swing. Once a
 // victim dies, the same DTs are sent again to the next nearest live enemy.
 run();if(ai.towns[0].orders!=2)return 2;
 units[2].dead=true;units[3].dead=true;run();
 if(units[0].order_target.unit!=&units[4]||units[1].order_target.unit!=&units[4]||ai.towns[0].orders!=4)return 3;
 // Mode zero keeps the strategic path and location semantics.
 reset();add(0,0,0);add(1,1,100);run(0);
 if(ai.strategic_calls!=1||ai.towns[0].orders||ai.towns[0].center.x!=42||ai.towns[0].center.y!=84||ai.read_count!=1)return 4;
 // Random suicide includes combat-capable workers and heroes; it does not
 // borrow the strategic-wave exclusions for either group.
 reset();add(0,0,0)->worker=true;add(1,0,10)->hero=true;add(2,1,200);run();
 if(ai.towns[0].orders!=2||units[0].order_target.unit!=&units[2]||units[1].order_target.unit!=&units[2])return 5;
 // A nearby invincible intro actor, allied unit, undetected enemy, flyer, or
 // unreachable target must not prevent a ground attacker finding a valid foe.
 reset();add(0,0,0);add(1,1,10)->invincible=true;add(2,2,20);ai.st.alliances[0][2]=true;
 add(3,1,30)->undetected=true;add(4,1,40)->flying=true;add(5,1,50,999);add(6,1,1100);
 run();if(units[0].order_target.unit!=&units[6])return 6;
 // With no valid targets the script leaves an existing order alone.
 reset();add(0,0,0);add(1,1,100)->invincible=true;run();
 if(ai.towns[0].orders||units[0].order_type!=&idle)return 7;
 // Do not disrupt a locked cinematic action or cast, load passengers, or
 // issue combat orders to buildings. Available combat units still respond.
 reset();add(0,0,0)->locked=true;add(1,0,10)->casting=true;add(2,0,20)->loaded=true;
 add(3,0,30)->building=true;add(4,0,40);add(5,1,100);run();
 if(ai.towns[0].orders!=1||units[4].order_target.unit!=&units[5])return 8;
 for(int i=0;i<4;++i)if(units[i].order_type!=&idle)return 9;
 return 0;
}
'''

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--cxx',default=os.environ.get('CXX'))
    parser.add_argument('--windows-ndk',action='store_true')
    args=parser.parse_args()
    source=(ROOT/'native-engine/web/campaign_ai.h').read_text(encoding='utf-8')
    start=source.index('    void ai_random_suicide(int owner) {')
    end=source.index('    void ai_run(thread& t)',start)
    op_start=source.index('            case 31:',end)
    op_end=source.index('\n',op_start)
    dispatch='    void run_opcode31(thread& t){auto& a=towns[t.owner];switch(31){\n'+source[op_start:op_end]+'\n}}\n'
    out=ROOT/'build-native/dunes-unit';out.mkdir(parents=True,exist_ok=True)
    cpp=out/'dunes.cpp';cpp.write_text(PRELUDE+source[start:end]+dispatch+POSTLUDE,encoding='utf-8')
    compiler=args.cxx or shutil.which('clang++') or shutil.which('g++')
    if not compiler:raise SystemExit('No host C++ compiler found; fixture generated at '+str(cpp))
    exe=out/('dunes.exe' if os.name=='nt' else 'dunes')
    flags=['-std=c++17','-O0']
    if args.windows_ndk:
        linker=Path(compiler).with_name('lld-link.exe')
        if not linker.exists():
            linker=out/'lld-link.exe';shutil.copyfile(Path(compiler).with_name('lld.exe'),linker)
        flags+=['--target=x86_64-pc-windows-msvc','--ld-path='+str(linker),'-nostdlib','-fno-builtin','-fno-stack-protector','-fno-exceptions','-fno-rtti','-Wl,/entry:main,/subsystem:console']
    subprocess.run([compiler,*flags,str(cpp),'-o',str(exe)],check=True)
    result=subprocess.run([str(exe)])
    if result.returncode:raise SystemExit('SuiR behavior assertion failed: '+str(result.returncode))
    print('PASS SuiR: mode dispatch; per-unit nearest target; repeat/retarget; workers/heroes; attackability/reachability; protected orders; strategic mode retained')

if __name__=='__main__':main()
