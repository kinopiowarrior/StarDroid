#pragma once
// Portable snapshot schema 4 (also reads schemas 1/2/3). Never persist process pointers, STL storage or padding.
// Pools are allocated before references are decoded; intrusive list order and free
// slot generations are retained because both affect deterministic continuation.
#include <unordered_map>
#include <unordered_set>
#include <type_traits>
#include <limits>
namespace native_save {
using namespace bwgame;
constexpr size_t MAX_BYTES=64*1024*1024;
inline void check(bool v,const char* why="Invalid saved game") {if(!v)throw std::runtime_error(why);}
inline uint64_t hash_bytes(const void* data,size_t n,uint64_t h=14695981039346656037ull) {
    auto p=static_cast<const uint8_t*>(data);while(n--) {h^=*p++;h*=1099511628211ull;}return h;
}
template<class C> uint64_t hash(const C& v,uint64_t h=14695981039346656037ull) {return hash_bytes(v.data(),v.size()*sizeof(v[0]),h);}
// Retired external-bot fields stay reserved for schema 1/2 decoding.
struct HostState {int bot_slot=-1;bool bot_enabled=false;unsigned bot_frame=0;std::array<std::vector<uint8_t>,4> turns;};
template<class T> struct Table {
    std::vector<const T*> items{nullptr};std::unordered_map<const T*,uint64_t> ids;
    void add(const T* p) {ids.emplace(p,items.size());items.push_back(p);}
    uint64_t id(const T* p) {if(!p)return 0;auto i=ids.find(p);check(i!=ids.end(),"Unregistered reference in saved game");return i->second;}
};
struct Codec {
    uint64_t version=4;
    bool reading;std::vector<uint8_t>& bytes;size_t pos=0;state& st;
    Codec(bool r,std::vector<uint8_t>& b,state& s):reading(r),bytes(b),st(s) {}
    template<class T> std::enable_if_t<std::is_integral<T>::value> io(T& v) {
        if(reading) {check(pos+8<=bytes.size(),"Truncated saved game");uint64_t n=0;for(int k=0;k<8;k++)n|=uint64_t(bytes[pos++])<<(8*k);
            if constexpr(std::is_signed<T>::value) {int64_t z=static_cast<int64_t>(n);check(z>=std::numeric_limits<T>::min()&&z<=std::numeric_limits<T>::max());v=T(z);}
            else {check(n<=uint64_t(std::numeric_limits<T>::max()));v=T(n);}
        } else {check(bytes.size()+8<=MAX_BYTES,"Saved game is too large");uint64_t n=uint64_t(v);for(int k=0;k<8;k++)bytes.push_back(uint8_t(n>>(8*k)));}
    }
    template<class T> std::enable_if_t<std::is_enum<T>::value> io(T& v) {int n=int(v);io(n);if(reading)v=T(n);}
    template<class T> auto io(T& v)->decltype((void)v.raw_value) {io(v.raw_value);}
    template<class T> void io(xy_t<T>& v) {fields(v.x,v.y);}
    template<class T> void io(rect_t<T>& v) {fields(v.from,v.to);}
    template<class A,class B> void io(std::pair<A,B>& v) {fields(v.first,v.second);}
    template<class T,size_t N> void io(std::array<T,N>& v) {for(auto& x:v)io(x);}
    template<class T,size_t N> void io(T (&v)[N]) {for(auto& x:v)io(x);}
    template<class T,class I,size_t N> void io(type_indexed_array<T,I,N>& v) {for(auto& x:v)io(x);}
    size_t count(size_t n,size_t limit=1000000) {io(n);check(n<=limit,"Saved game collection is too large");if(reading)check(n<=bytes.size()-pos);return n;}
    template<class C> void sequence(C& v,size_t max=1000000) {size_t n=count(v.size(),max);if(reading)v.resize(n);for(auto& x:v)io(x);}
    template<class T,class A> void io(std::vector<T,A>& v) {sequence(v);}
    template<class T,class A> void io(std::deque<T,A>& v) {sequence(v);}
    template<class T,size_t N> void io(static_vector<T,N>& v) {sequence(v,N);}
    template<class T,class A> void io(circular_vector<T,A>& v) {size_t n=count(v.size());if(reading){v.clear();for(size_t i=0;i<n;i++){T x{};io(x);v.push_back(x);}}else for(auto& x:v)io(x);}
    template<class C,class A> void io(std::basic_string<char,C,A>& v) {size_t n=count(v.size());if(reading){check(n<=bytes.size()-pos);v.assign(reinterpret_cast<const char*>(bytes.data()+pos),n);pos+=n;}else {check(bytes.size()+n<=MAX_BYTES);bytes.insert(bytes.end(),v.begin(),v.end());}}
    template<class K,class V,class H,class E,class A> void io(std::unordered_map<K,V,H,E,A>& v) {size_t n=count(v.size());if(reading){v.clear();for(size_t i=0;i<n;i++){K k{};V x{};fields(k,x);check(v.emplace(k,std::move(x)).second);}}else {std::vector<K> keys;for(auto& x:v)keys.push_back(x.first);std::sort(keys.begin(),keys.end());for(auto k:keys){io(k);io(v.at(k));}}}
    template<class K,class H,class E,class A> void io(std::unordered_set<K,H,E,A>& v) {size_t n=count(v.size());if(reading){v.clear();for(size_t i=0;i<n;i++){K k{};io(k);check(v.insert(k).second);}}else {std::vector<K> keys(v.begin(),v.end());std::sort(keys.begin(),keys.end());for(auto k:keys)io(k);}}
    template<class... T> void fields(T&... v) {(io(v),...);}
#define TABLE(T,N) Table<T> N; Table<T>& table(const T*) {return N;}
    TABLE(unit_t,units) TABLE(bullet_t,bullets) TABLE(sprite_t,sprites) TABLE(image_t,images) TABLE(order_t,orders)
    TABLE(path_t,paths) TABLE(thingy_t,thingies) TABLE(creep_life_t::entry,creep)
    TABLE(unit_type_t,unitTypes) TABLE(weapon_type_t,weaponTypes) TABLE(upgrade_type_t,upgradeTypes) TABLE(tech_type_t,techTypes)
    TABLE(flingy_type_t,flingyTypes) TABLE(sprite_type_t,spriteTypes) TABLE(image_type_t,imageTypes) TABLE(order_type_t,orderTypes)
    TABLE(grp_t,grps) TABLE(iscript_t::script,scripts) TABLE(regions_t::region,regions) TABLE(trigger,triggers)
#undef TABLE
    template<class T> void io(T*& v) {auto& t=table(v);uint64_t id=reading?0:t.id(v);io(id);check(id<t.items.size(),"Invalid saved game reference");if(reading)v=const_cast<T*>(t.items[id]);}
    template<class T,class F,std::pair<T*,T*> T::* M> void io(intrusive_list<T,F,M>& v) {
        size_t n=0;if(!reading)for(auto& x:v)++n;n=count(n,20000);
        if(reading) {v.clear();std::unordered_set<T*> seen;for(size_t i=0;i<n;i++){T* p=nullptr;io(p);check(p&&seen.insert(p).second,"Invalid saved game list");v.push_back(*p);}}
        else for(auto& x:v){T* p=&x;io(p);}
    }
    template<class C> void register_values(const C& c) {for(auto& x:c)table(&x).add(&x);}
    template<class T,size_t N,size_t G> void pool_header(object_container<T,N,G>& p) {
        size_t n=p.size;io(n);check(n<=N&&n%G==0);if(reading)while(p.size<n)p.grow(false);
        for(auto& block:p.list)for(auto& x:block) {
            if constexpr(std::is_same<T,unit_t>::value)if(reading){auto index=x.index;memset(&x,0,sizeof(x));new (&x)unit_t();x.index=index;}
            table(&x).add(&x);
        }
    }
    template<class T,size_t N,size_t G> void pool_data(object_container<T,N,G>& p) {
        io(p.free_list);std::unordered_set<const T*> free;for(auto& x:p.free_list)free.insert(&x);
        for(auto& block:p.list)for(auto& x:block) {
            // A recycled unit's generation remains significant even while free.
            if constexpr(std::is_same<T,unit_t>::value)io(x.unit_id_generation);
            if(!free.count(&x))io(x);
        }
    }
    template<class T,class A> void list_header(std::list<T,A>& v) {size_t n=count(v.size(),20000);if(reading)v.resize(n);register_values(v);}
    void references() {
        auto& g=*st.game;auto& a=*st.global;
        register_values(g.unit_types.vec);register_values(g.weapon_types.vec);register_values(g.upgrade_types.vec);register_values(g.tech_types.vec);
        register_values(a.flingy_types.vec);register_values(a.sprite_types.vec);register_values(a.image_types.vec);register_values(a.order_types.vec);register_values(a.grps);
        // unordered_map iteration order must never define on-disk references.
        std::vector<unsigned> keys;for(auto& x:a.iscript.scripts)keys.push_back(x.first);std::sort(keys.begin(),keys.end());for(auto k:keys)scripts.add(&a.iscript.scripts.at(k));
        register_values(g.regions.regions);register_values(g.triggers);
        pool_header(st.units_container);pool_header(st.bullets_container);pool_header(st.sprites_container);pool_header(st.images_container);pool_header(st.orders_container);
        list_header(st.paths);list_header(st.thingies);register_values(st.creep_life.entry_container);
    }
    void io(player_t& v) { fields(v.controller,v.race,v.force,v.color,v.initially_active,v.victory_state); }
    void io(running_trigger::action& v) { fields(v.flags); }
    void io(running_trigger& v) { fields(v.actions,v.t,v.flags,v.current_action_index); }
    void io(location& v) { fields(v.area,v.elevation_flags); }
    void io(tile_t& v) { fields(v.visible,v.explored,v.flags); }
    void io(target_t& v) { fields(v.pos,v.unit); }
    void io(iscript_state_t& v) { fields(v.current_script,v.program_counter,v.return_address,v.animation,v.wait); }
    void io(image_t& v) { fields(v.index,v.image_type,v.modifier,v.frame_index_offset,v.flags,v.offset,v.iscript_state,v.frame_index_base,v.frame_index,v.grp,v.modifier_data1,v.modifier_data2,v.sprite,v.frozen_y_value); }
    void io(sprite_t& v) { fields(v.index,v.sprite_type,v.owner,v.selection_index,v.visibility_flags,v.elevation_level,v.flags,v.selection_timer,v.width,v.height,v.position,v.main_image,v.images); }
    void io(thingy_t& v) { fields(v.hp,v.sprite); }
    void io(flingy_t& v) { io(static_cast<thingy_t&>(v)); fields(v.move_target,v.next_movement_waypoint,v.next_target_waypoint,v.movement_flags,v.heading,v.flingy_turn_rate,v.next_velocity_direction,v.flingy_type,v.flingy_movement_type,v.position,v.exact_position,v.flingy_top_speed,v.current_speed,v.next_speed,v.velocity,v.flingy_acceleration,v.current_velocity_direction,v.desired_velocity_direction,v.order_signal); }
    void io(bullet_t& v) { io(static_cast<flingy_t&>(v)); fields(v.index,v.bullet_state,v.bullet_target,v.bullet_target_pos,v.weapon_type,v.remaining_time,v.hit_flags,v.remaining_bounces,v.owner,v.bullet_owner_unit,v.prev_bounce_unit,v.hit_near_target_position_index); }
    void io(order_target_t& v) { fields(v.position,v.unit,v.unit_type); }
    void io(order_t& v) { fields(v.index,v.order_type,v.target); }
    void io(path_t& v) { fields(v.delay,v.creation_frame,v.state_flags,v.long_path,v.full_long_path_size,v.short_path,v.current_long_path_index,v.current_short_path_index,v.source,v.destination,v.next,v.last_collision_unit,v.last_collision_speed,v.slide_free_direction); }
    void io(state_base_non_copyable::unit_finder_entry& v) { fields(v.u,v.value); }
    void io(action_state& v) { fields(v.player_id,v.actions_data_position,v.next_action_frame,v.selection,v.control_groups); }
    void io(campaign_ai::defence_alert& v) { fields(v.source,v.victim,v.wake,v.until); }
    void io(campaign_ai::request& v) { fields(v.type,v.amount,v.priority); }
    void io(campaign_ai::airlift& v) { fields(v.transport,v.passengers,v.target,v.landing,v.phase,v.started,v.retry,v.build_type);check(v.phase>=0&&v.phase<=1&&v.build_type>=-1&&v.build_type<228&&v.passengers.size()<=8); }
    void io(campaign_ai::town& v) { fields(v.active,v.campaign,v.supply,v.center,v.builds,v.techs,v.upgrades,v.defence,v.wave,v.limits,v.nuke_rate,v.attackers,v.built,v.trained,v.attacks,v.orders,v.opcodes);if(version>=4)sequence(v.lifts,4); }
    void io(campaign_ai::thread& v) { fields(v.owner,v.pc,v.wake,v.file,v.calls,v.id,v.pos,v.done); }
    void io(play_ui::alert_t& v) { fields(v.unit,v.pos,v.ttl,v.kind); }
    void io(play_ui::log_msg& v) { fields(v.text,v.age); }
    void io(ui_functions::fog_snapshot_t& v) { fields(v.valid,v.images); }
    void io(game_load_functions::setup_info_t& v) { fields(v.create_melee_units_for_player,v.victory_condition,v.tournament_mode,v.starting_units,v.resource_type,v.starting_minerals,v.create_no_units); }
    void io(replay_saver_state& v) { fields(v.history,v.current_history_frame,v.current_actions_size,v.current_actions_size_index,v.current_actions_size_offset,v.random_seed,v.player_name,v.map_tile_width,v.map_tile_height,v.active_player_count,v.slot_count,v.game_speed,v.game_type,v.tileset,v.game_name,v.map_name,v.setup_info,v.players,v.player_names); }
    void io(HostState& v) { fields(v.bot_slot,v.bot_enabled,v.bot_frame,v.turns); }
    void io(unit_t& v) {
        io(static_cast<flingy_t&>(v));
        fields(v.index,v.owner,v.order_type,v.order_state,v.order_unit_type,v.main_order_timer,v.ground_weapon_cooldown,v.air_weapon_cooldown,v.spell_cooldown,v.order_target);
        fields(v.shield_points,v.unit_type,v.subunit,v.order_queue,v.auto_target_unit,v.connected_unit,v.order_queue_count,v.order_process_timer,v.unknown_0x086,v.attack_notify_timer);
        fields(v.previous_unit_type,v.last_event_timer,v.last_event_color,v.rank_increase,v.kill_count,v.last_attacking_player,v.secondary_order_timer,v.user_action_flags,v.cloak_counter,v.movement_state);
        fields(v.build_queue,v.build_queue_limbo,v.energy,v.secondary_order_type,v.damage_overlay_state,v.hp_construction_rate,v.shield_construction_rate,v.remaining_build_time,v.previous_hp,v.loaded_units);
        fields(v.status_flags,v.carrying_flags,v.wireframe_randomizer,v.secondary_order_state,v.move_target_timer,v.detected_flags,v.current_build_unit,v.path,v.pathing_collision_counter,v.pathing_flags);
        fields(v.unused_0x106,v.is_being_healed,v.terrain_no_collision_bounds,v.remove_timer,v.defensive_matrix_hp,v.defensive_matrix_timer,v.stim_timer,v.ensnare_timer,v.lockdown_timer,v.irradiate_timer);
        fields(v.stasis_timer,v.plague_timer,v.storm_timer,v.irradiated_by,v.irradiate_owner,v.parasite_flags,v.cycle_counter,v.blinded_by,v.maelstrom_timer,v.acid_spore_count);
        fields(v.acid_spore_time,v.next_hit_near_target_position_index,v.air_strength,v.ground_strength,v.repulse_flags,v.repulse_direction,v.repulse_index,v.unit_finder_bounding_box,v.unit_finder_visited,v.unit_finder_index_from);
        fields(v.unit_finder_index_to);
        check(v.unit_type && v.owner>=0 && v.owner<12 && v.order_type && v.secondary_order_type);
        state_functions f(st);
        if(f.unit_is_vulture(&v))io(v.vulture.spider_mine_count);
        if(f.unit_is(&v,UnitTypes::Protoss_Interceptor)||f.unit_is(&v,UnitTypes::Protoss_Scarab))fields(v.fighter.parent,v.fighter.is_outside);
        if(f.unit_is_carrier(&v)||f.unit_is_reaver(&v))fields(v.carrier.inside_units,v.carrier.outside_units,v.carrier.inside_count,v.carrier.outside_count);
        if(f.unit_is_special_beacon(&v))fields(v.beacon.unknown_00,v.beacon.unknown_04,v.beacon.flag_spawn_frame);
        if(f.unit_is_ghost(&v))io(v.ghost.nuke_dot);
        if(f.ut_worker(&v))fields(v.worker.powerup,v.worker.target_resource_position,v.worker.target_resource_unit,v.worker.repair_timer,v.worker.is_gathering,v.worker.resources_carried,v.worker.gather_target);
        if(f.ut_building(&v)) {
            fields(v.building.addon,v.building.addon_build_type,v.building.upgrade_research_time,v.building.researching_type,v.building.upgrading_type,v.building.larva_timer,v.building.is_landing,v.building.creep_timer,v.building.upgrading_level,v.building.rally);
        }
        if(f.ut_resource(&v))fields(v.building.resource.resource_count,v.building.resource.resource_iscript,v.building.resource.is_being_gathered,v.building.resource.gather_queue);
        if(f.unit_is(&v,UnitTypes::Zerg_Nydus_Canal))io(v.building.nydus.exit);
        if(f.unit_is(&v,UnitTypes::Protoss_Pylon))io(v.building.pylon.psi_field_sprite);
        if(f.unit_is(&v,UnitTypes::Terran_Nuclear_Silo))fields(v.building.silo.nuke,v.building.silo.ready);
        if(f.ut_powerup(&v))io(v.building.powerup.origin);
        if(f.unit_is_hatchery(&v))io(v.building.hatchery.larva_spawn_side_values);
    }
    void io(creep_life_t& v) {
        fields(v.recede_timer,v.check_dead_unit_timer);
        for(auto& e:v.entry_container)fields(e.tile_pos,e.n_neighboring_creep_tiles);
        // Clear every old head before installing links (constructor populates free_list).
        if(reading) {for(auto& l:v.lists)l.clear();v.free_list.clear();for(auto& l:v.table.buckets)l.clear();}
        fields(v.lists,v.lists_size,v.free_list,v.free_list_size,v.table.buckets);
    }
    void world() {
        references();
        fields(st.update_tiles_countdown,st.order_timer_counter,st.secondary_order_timer_counter,st.current_frame,st.players,st.alliances,st.upgrade_levels,st.upgrade_upgrading);
        fields(st.tech_researched,st.tech_researching,st.unit_counts,st.completed_unit_counts,st.factory_counts,st.building_counts,st.non_building_counts,st.completed_factory_counts);
        fields(st.completed_building_counts,st.completed_non_building_counts,st.total_buildings_ever_completed,st.total_non_buildings_ever_completed,st.unit_score,st.building_score,st.supply_used,st.supply_available);
        fields(st.shared_vision,st.tiles,st.tiles_mega_tile_index,st.random_counts,st.total_random_counts,st.lcg_rand_state,st.last_error,st.trigger_timer);
        fields(st.running_triggers,st.trigger_wait_timers,st.trigger_waiting,st.active_orders_size,st.active_bullets_size,st.active_thingies_size,st.repulse_field,st.prev_bullet_heading_offset_clockwise);
        fields(st.current_minerals,st.current_gas,st.total_minerals_gathered,st.total_gas_gathered,st.recent_lurker_hits,st.recent_lurker_hit_current_index,st.creep_life,st.update_psionic_matrix);
        fields(st.disruption_webbed_units,st.cheats_enabled,st.cheat_operation_cwal,st.locations);
        pool_data(st.units_container);pool_data(st.bullets_container);pool_data(st.sprites_container);pool_data(st.images_container);pool_data(st.orders_container);
        for(auto& p:st.paths)io(p);for(auto& t:st.thingies)io(t);
        fields(st.visible_units,st.hidden_units,st.map_revealer_units,st.dead_units,st.player_units,st.cloaked_units,st.psionic_matrix_units,
            st.active_bullets,st.sprites_on_tile_line,st.free_paths,st.active_thingies,st.free_thingies,
            st.unit_finder_x,st.unit_finder_y,st.consider_collision_with_unit_bug,st.prev_bullet_source_unit);
        check(st.current_frame>=0 && st.tiles.size()==st.game->map_tile_width*st.game->map_tile_height);
        check(st.recent_lurker_hit_current_index<st.recent_lurker_hits.size());
    }
    void presentation(play_ui& u,campaign_ui* c) {
        fields(u.my_player,u.my_race,u.spectator,u.competitive,u.campaign_control_lock,u.campaign_speech_muted,u.sound_rotation,u.ack_unit);
        fields(u.ack_count,u.screen_pos,u.fog_player,u.current_selection,u.current_action_state,u.groups,u.outgoing,u.test_instant);
        fields(u.outcome,u.score_killed,u.score_razed,u.score_lost,u.score_buildings_lost,u.score_kills,u.score_razings,u.alerts);
        fields(u.last_event_pos,u.have_last_event,u.last_life,u.last_stim,u.announced,u.ready_notifications,u.last_ready_sound,u.last_ready_type);
        fields(u.events_seeded,u.msg_log,u.player_had_units,u.player_eliminated,u.alert_cooldown,u.event_tick,u.advisor_queue,u.technology_notifications);
        fields(u.rescue_notifications,u.last_rescue_sound,u.fog_snapshots,u.rep,u.rep_on);
        if(version>=4)sequence(u.pending_larva_morphs,12);
        if(c) {
            fields(c->units_paused,c->objectives,c->countdown_ms,c->timer_paused,c->deaths,c->scores,c->counted_deaths,c->switches);
            fields(c->kills,c->events,c->towns,c->threads,c->spawned,c->ai_actions,c->ai_error,c->ai_trace);
            fields(c->ai_clock,c->ai_random);
            if(version>=2)io(c->defence_alerts);
        }
        check(u.my_player>=0&&u.my_player<8&&int(u.my_race)>=0&&int(u.my_race)<3);
    }
};
inline uint64_t assets_signature(play_ui& ui,campaign_ui* campaign) {
    auto& g=ui.global_st;uint64_t h=hash(ui.rep_map);h=hash(g.units_dat,h);h=hash(g.weapons_dat,h);h=hash(g.upgrades_dat,h);h=hash(g.techdata_dat,h);
    h=hash(g.iscript.program_data,h);h=hash(g.tileset_vf4.at(ui.game_st.tileset_index),h);h=hash(g.tileset_cv5.at(ui.game_st.tileset_index),h);
    if(campaign)for(auto& b:campaign->scripts)h=hash(b,h);return h;
}
inline void header(Codec& c,play_ui& ui,campaign_ui* campaign) {
    uint64_t magic=0x3156415357424f,version=c.version,signature=assets_signature(ui,campaign);bool isCampaign=campaign;
    auto expected=signature;c.fields(magic,version,signature,isCampaign);
    check(magic==0x3156415357424f&&(version>=1&&version<=4),"Unsupported saved game version");
    c.version=version;
    bool heroes=version>=3?ui.game_st.hero_mode:false,teams=version>=3?ui.game_st.team_game:false;
    if(version>=3)c.fields(heroes,teams);
    check(heroes==ui.game_st.hero_mode&&teams==ui.game_st.team_game,"This save needs the same Hero Mode and team settings");
    check(signature==expected&&isCampaign==bool(campaign),"This save needs the same map and game data");
}
inline std::vector<uint8_t> save(play_ui& ui,campaign_ui* campaign,HostState& host) {
    std::vector<uint8_t> b;Codec c(false,b,ui.st);header(c,ui,campaign);c.io(host);c.world();c.presentation(ui,campaign);uint64_t checksum=hash(b);c.io(checksum);return b;
}
inline void verify(const std::vector<uint8_t>& bytes) {
    check(bytes.size()>=40&&bytes.size()<=MAX_BYTES,"Invalid saved game size");
    uint64_t expected=0;for(int k=0;k<8;k++)expected|=uint64_t(bytes[bytes.size()-8+k])<<(8*k);
    check(expected==hash_bytes(bytes.data(),bytes.size()-8),"Saved game is damaged");
}
inline HostState restore(play_ui& ui,campaign_ui* campaign,std::vector<uint8_t>& bytes) {
    verify(bytes);
    auto next=std::make_unique<state>();next->global=ui.st.global;next->game=ui.st.game;
    game_player player;player.set_st(*next);auto presentation=std::make_unique<campaign_ui>(std::move(player),ui.my_player,ui.my_race);
    Codec c(true,bytes,*next);header(c,ui,campaign);HostState host;c.io(host);check(!host.bot_enabled,"This save uses the removed Zerg skirmish AI (legacy external bot); start a new skirmish");c.world();c.presentation(*presentation,campaign?presentation.get():nullptr);
    check(c.pos==bytes.size()-8,"Unexpected saved game data");check(host.bot_slot>=-1&&host.bot_slot<8);
    // No live state is touched until the entire snapshot has decoded successfully.
    ui.st=std::move(*next);auto& n=*presentation;
    ui.my_player=std::move(n.my_player);
    ui.my_race=std::move(n.my_race);
    ui.spectator=std::move(n.spectator);
    ui.competitive=std::move(n.competitive);
    ui.campaign_control_lock=std::move(n.campaign_control_lock);
    ui.campaign_speech_muted=std::move(n.campaign_speech_muted);
    ui.sound_rotation=std::move(n.sound_rotation);
    ui.ack_unit=std::move(n.ack_unit);
    ui.ack_count=std::move(n.ack_count);
    ui.screen_pos=std::move(n.screen_pos);
    ui.fog_player=std::move(n.fog_player);
    ui.current_selection=std::move(n.current_selection);
    ui.current_action_state=std::move(n.current_action_state);
    ui.groups=std::move(n.groups);
    ui.outgoing=std::move(n.outgoing);
    ui.test_instant=std::move(n.test_instant);
    ui.outcome=std::move(n.outcome);
    ui.score_killed=std::move(n.score_killed);
    ui.score_razed=std::move(n.score_razed);
    ui.score_lost=std::move(n.score_lost);
    ui.score_buildings_lost=std::move(n.score_buildings_lost);
    ui.score_kills=std::move(n.score_kills);
    ui.score_razings=std::move(n.score_razings);
    // Rebuild the duplicate-death guard from the restored world. Keeping IDs
    // from the discarded future suppresses losses when those units die again.
    // This is derived state, so existing schema 1/2 saves remain compatible.
    ui.scored_losses.clear();
    for (int owner=0;owner<12;++owner) for (auto* unit:ptr(ui.st.player_units[owner]))
        if (ui.unit_dying(unit)) ui.scored_losses.insert(ui.get_unit_id(unit).raw_value);
    for (auto* unit:ptr(ui.st.dead_units)) ui.scored_losses.insert(ui.get_unit_id(unit).raw_value);
    ui.alerts=std::move(n.alerts);
    ui.last_event_pos=std::move(n.last_event_pos);
    ui.have_last_event=std::move(n.have_last_event);
    ui.last_life=std::move(n.last_life);
    ui.last_stim=std::move(n.last_stim);
    ui.announced=std::move(n.announced);
    ui.ready_notifications=std::move(n.ready_notifications);
    ui.last_ready_sound=std::move(n.last_ready_sound);
    ui.last_ready_type=std::move(n.last_ready_type);
    ui.ready_portrait_serial=0;ui.ready_portrait_type=-1;
    ui.events_seeded=std::move(n.events_seeded);
    ui.msg_log=std::move(n.msg_log);
    ui.player_had_units=std::move(n.player_had_units);
    ui.player_eliminated=std::move(n.player_eliminated);
    ui.alert_cooldown=std::move(n.alert_cooldown);
    ui.event_tick=std::move(n.event_tick);
    ui.advisor_queue=std::move(n.advisor_queue);
    ui.technology_notifications=std::move(n.technology_notifications);
    ui.rescue_notifications=std::move(n.rescue_notifications);
    ui.last_rescue_sound=std::move(n.last_rescue_sound);
    ui.fog_snapshots=std::move(n.fog_snapshots);
    ui.rep=std::move(n.rep);
    ui.rep_on=std::move(n.rep_on);
    if(campaign) {
        campaign->units_paused=std::move(n.units_paused);
        campaign->objectives=std::move(n.objectives);
        campaign->countdown_ms=std::move(n.countdown_ms);
        campaign->timer_paused=std::move(n.timer_paused);
        campaign->deaths=std::move(n.deaths);
        campaign->scores=std::move(n.scores);
        campaign->counted_deaths=std::move(n.counted_deaths);
        campaign->switches=std::move(n.switches);
        campaign->kills=std::move(n.kills);
        campaign->events=std::move(n.events);
        campaign->towns=std::move(n.towns);
        campaign->threads=std::move(n.threads);
        campaign->spawned=std::move(n.spawned);
        campaign->ai_actions=std::move(n.ai_actions);
        campaign->ai_error=std::move(n.ai_error);
        campaign->ai_trace=std::move(n.ai_trace);
        campaign->ai_clock=std::move(n.ai_clock);
        campaign->ai_random=std::move(n.ai_random);
        campaign->defence_alerts=std::move(n.defence_alerts);
    }
    ui.rep.map_data=ui.rep_map.data();ui.rep.map_data_size=ui.rep_map.size();ui.rep_out.clear();
    ui.iscript_unit=nullptr;ui.iscript_bullet=nullptr;ui.iscript_flingy=nullptr;ui.unit_finder_search_index=0;
    ui.current_selection_sprites.clear();std::fill(ui.current_selection_sprites_set.begin(),ui.current_selection_sprites_set.end(),nullptr);
    ui.pending_larva_morphs=std::move(n.pending_larva_morphs);
    ui.pending_build=ui.pending_addon=nullptr;ui.pending_land=ui.pending_nydus=ui.targeting=false;ui.menu=0;ui.card.clear();ui.advisor_last=-1;
    ui.mouse_x=ui.mouse_y=-100;ui.last_recalled_group=-1;ui.flash_start=-1;ui.clamp_viewport();
    for(auto& r:ui.st.game->regions.regions){r.pathfinder_flag=0;r.pathfinder_node=nullptr;}
    return host;
}
} // namespace native_save
