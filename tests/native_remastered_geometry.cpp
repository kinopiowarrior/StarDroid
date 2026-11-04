#include "../native/remastered/geometry.h"
#include <iostream>
namespace scr = stardroid::remastered;
static unsigned checks;
static void need(bool ok, const char* why) { ++checks; if (!ok) throw std::runtime_error(why); }
int main() {
    try {
        // Real Scourge frame-0 geometry, expressed as plain format values only.
        scr::Frame f{0,0,53,57,94,80,0};
        auto p = scr::place_frame_hd2(f,196,182,100,80,false);
        need(p.left == 177 && p.top == 143 && p.width == 47 && p.height == 40,
             "Signed quarter-pixel anchor was rounded before centering");
        auto q = scr::place_frame_hd2(f,196,182,100,80,true);
        need(q.left == 176 && q.top == 143, "Mirrored frame drifted from image center");
        need(p.left + q.left + int32_t(p.width) == 400, "Mirror endpoints are asymmetric");
        auto moved = scr::place_frame_hd2(f,196,182,-100,-80,false);
        need(moved.left == -223 && moved.top == -177, "Negative camera-relative coordinate was clamped");
        f.x_off = -3; f.y_off = -5;
        p = scr::place_frame_hd2(f,381,514,0,0,false);
        need(p.left == -97 && p.top == -131, "Negative frame offset did not floor correctly");
        f.width = 0; f.height = 0;
        p = scr::place_frame_hd2(f,381,514,0,0,false);
        need(p.width == 0 && p.height == 0, "Empty frame invented a visible extent");
        bool rejected = false;
        try { (void)scr::place_frame_hd2(f,0,514,0,0,false); } catch (const scr::FormatError&) { rejected = true; }
        need(rejected, "Zero canvas silently accepted");
        rejected = false;
        try { (void)scr::place_frame_hd2(f,381,514,std::numeric_limits<int32_t>::max(),0,false); }
        catch (const scr::FormatError&) { rejected = true; }
        need(rejected, "World-to-render coordinate overflow accepted");
        std::cout << "PASS " << checks << " HD2 anchor checks\n";
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
