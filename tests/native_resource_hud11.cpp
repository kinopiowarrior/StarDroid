// Native selection-contract regression for gas buildings and raw resources.
// Requires the user's original core MPQs; no game data is bundled by this test.
#include "../native/runtime.cpp"
#include <iostream>
#include <sstream>
using namespace bwgame;
static int checks = 0;
static void check(bool ok, const char* why) {
    ++checks;
    if (!ok) throw std::runtime_error(why);
}
static std::vector<std::string> fields(const std::string& text) {
    const std::string line = text.substr(0, text.find('\n'));
    std::vector<std::string> result;
    size_t at = 0;
    do {
        size_t end = line.find('\t', at);
        result.push_back(line.substr(at, end == std::string::npos ? end : end - at));
        if (end == std::string::npos) break;
        at = end + 1;
    } while (true);
    return result;
}
static unit_t* fixture(UnitTypes type, int owner) {
    // The contract does not depend on map placement or simulation. Keep synthetic
    // completed units at one stable point, as native_hotkeys11 does for its cards.
    auto* u = g_ui->create_completed_unit(g_ui->get_unit_type(type), {1024, 1024}, owner);
    check(u != nullptr, "Cannot create HUD fixture");
    g_ui->u_unset_status_flag(u, unit_t::status_flag_disabled);
    return u;
}
static std::vector<std::string> select(unit_t* u) {
    g_ui->current_selection_clear();
    g_ui->current_selection_add(u);
    g_ui->on_selection(false);
    g_ui->refresh_card();
    auto title = fields(g_ui->card_text.c_str());
    check(title.size() >= 4, "Incomplete selected-unit header");
    return title;
}
int main(int argc, char** argv) { try {
    check(argc == 2, "Usage: native-resource-hud11 CORE_MPQ_DIRECTORY");
    std::string core = argv[1];
    open_archive(0, core + "/STARDAT.MPQ");
    open_archive(1, core + "/BROODAT.MPQ");
    open_archive(2, core + "/patch_rt.mpq");
    open_archive(3, "app/src/main/assets/native/weave.scx");
    int races[] = {2, 0, 1, 1, 1, 1, 1, 1};
    int slots[] = {1, 0, 0, 0, 0, 0, 0, 0};
    configured_init(640, 480, 0, races, slots, 0, true, false, false);
    g_ui->fog_player = -1;
    g_ui->competitive = false;
    for (auto type : {UnitTypes::Protoss_Assimilator, UnitTypes::Terran_Refinery, UnitTypes::Zerg_Extractor}) {
        auto* u = fixture(type, 0);
        u->hp = fp8::integer(234);
        if (u->unit_type->has_shield) u->shield_points = fp8::integer(123);
        for (int remaining : {4321, 0}) {
            u->building.resource.resource_count = remaining;
            auto title = select(u);
            check(title[2] == "Gas " + std::to_string(remaining), "Gas building replaced gas amount with Shields");
            check(title[1].find("HP 234/") == 0, "Refinery HP disappeared");
            std::string details = g_ui->unit_details();
            auto stats = fields(details);
            check(stats.size() == 12, "Gas building missing native life/details contract");
            check(stats[5] == "1", "Gas building displayed as a unit");
            if (type == UnitTypes::Protoss_Assimilator) {
                check(stats[1] == "123" && std::stoi(stats[2]) == u->unit_type->shield_points,
                      "Assimilator shields missing from combined life readout");
                check(details.find("\nshield\t") != std::string::npos, "Assimilator shield upgrade detail missing");
            } else check(stats[1] == "-1", "Non-Protoss gas building gained shields");
        }
    }
    for (auto type : {UnitTypes::Resource_Mineral_Field, UnitTypes::Resource_Mineral_Field_Type_2,
                      UnitTypes::Resource_Mineral_Field_Type_3, UnitTypes::Resource_Vespene_Geyser}) {
        auto* u = fixture(type, 11);
        u->building.resource.resource_count = 765;
        auto title = select(u);
        bool gas = type == UnitTypes::Resource_Vespene_Geyser;
        check(title[2] == (gas ? "Gas 765" : "Minerals 765"), "Raw resource amount changed");
        check(title[1].empty() && std::string(g_ui->unit_details()).empty(), "Raw resources gained health/armor details");
    }
    auto* caster = fixture(UnitTypes::Protoss_High_Templar, 0);
    caster->shield_points = fp8::integer(25);
    caster->energy = fp8::integer(75);
    auto title = select(caster);
    check(title[2].find("Shields 25/") == 0 && title[2].find("Energy 75/") != std::string::npos,
          "Non-resource Protoss shield/energy readout changed");
    auto stats = fields(g_ui->unit_details());
    check(stats.size() == 12 && stats[1] == "25" && stats[3] == "75", "Caster life/energy details changed");
    close_game();
    std::cout << "PASS " << checks << " resource HUD checks: three refinery races, depleted gas, four raw resources, Protoss caster\n";
    return 0;
} catch (const std::exception& e) {
    std::cerr << "RESOURCE_HUD11_FAIL " << e.what() << '\n';
    return 1;
} }
