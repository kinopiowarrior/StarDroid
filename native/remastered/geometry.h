#ifndef STARDROID_REMASTERED_GEOMETRY_H
#define STARDROID_REMASTERED_GEOMETRY_H

#include "assets.h"

namespace stardroid { namespace remastered {

struct FramePlacement {
    int32_t left = 0, top = 0;
    uint32_t width = 0, height = 0;
};

// A deterministic software-preview convention, not a retail rasterizer claim.
// ANIM canvas dimensions and frame offsets use 4x coordinates. Keep the signed
// quarter-pixel anchor until the final floor-to-HD2-pixel step; truncating the
// offset and canvas separately loses information on real odd-valued offsets.
// Mirror the snapped rectangle about the image center so the two orientations
// cannot drift by a pixel through different signed rounding.
inline FramePlacement place_frame_hd2(const Frame& frame, uint16_t canvas_width_hd,
    uint16_t canvas_height_hd, int32_t center_x_world, int32_t center_y_world,
    bool flipped) {
    auto floor_four = [](int64_t value) {
        return value >= 0 ? value / 4 : -((-value + 3) / 4);
    };
    auto checked = [](int64_t value) {
        if (value < std::numeric_limits<int32_t>::min() || value > std::numeric_limits<int32_t>::max())
            throw FormatError("HD2 placement exceeds signed coordinate range");
        return int32_t(value);
    };
    if (!canvas_width_hd || !canvas_height_hd)
        throw FormatError("HD2 preview requires nonzero ANIM canvas dimensions");
    const uint32_t width = frame.width / 2, height = frame.height / 2;
    int64_t x = floor_four(int64_t(frame.x_off) * 2 - canvas_width_hd);
    if (flipped) x = -x - width;
    const int64_t y = floor_four(int64_t(frame.y_off) * 2 - canvas_height_hd);
    return {checked(int64_t(center_x_world) * 2 + x),
        checked(int64_t(center_y_world) * 2 + y), width, height};
}

inline FramePlacement place_frame(const Anim& anim, size_t index,
    int32_t center_x_world, int32_t center_y_world, bool flipped) {
    if (anim.scale() != 2) throw FormatError("HD2 placement requires a 2x animation");
    if (index >= anim.frames().size()) throw FormatError("ANIM frame index out of range");
    return place_frame_hd2(anim.frames()[index], anim.canvas_width_hd(), anim.canvas_height_hd(),
        center_x_world, center_y_world, flipped);
}

}} // namespace stardroid::remastered
#endif
