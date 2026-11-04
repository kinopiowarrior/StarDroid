#ifndef STARDROID_HD2_COMPOSITOR_H
#define STARDROID_HD2_COMPOSITOR_H

#include "runtime.h"
#include <cstring>

namespace stardroid { namespace remastered { namespace live {

// The indexed plane stays authoritative for classic drawing. Only logical
// pixels covered by an imported body need four independent full-color samples.
// Keep small contiguous blocks indexed by framebuffer offset, so even an
// identical-color foreground write clears coverage without comparing colors.
class DetailPlane {
    using Color = std::array<uint8_t, 4>;
    using Quad = std::array<Color, 4>;
    struct Block {
        uint32_t live = 0;
        size_t key = 0;
        std::array<Quad, 32> quads;
    };
    static constexpr size_t max_blocks = (16u << 20) / sizeof(Block);
    std::vector<uint32_t> slots;
    std::vector<Block> blocks;
    size_t used = 0, pitch = 0, extent = 0;
    const uint8_t* base = nullptr;
    int width = 0, height = 0;

    void forget() noexcept {
        for (size_t i = 0; i < used; ++i) slots[blocks[i].key] = 0;
        used = 0;
        base = nullptr;
    }
    Quad* covered(size_t offset) {
        const uint32_t slot = slots[offset >> 5];
        if (!slot) return nullptr;
        auto& block = blocks[slot - 1];
        const unsigned i = offset & 31;
        return (block.live & (uint32_t(1) << i)) ? &block.quads[i] : nullptr;
    }
    Quad& acquire(size_t offset, const uint8_t* background) {
        const size_t key = offset >> 5;
        uint32_t& slot = slots[key];
        if (!slot) {
            if (used == max_blocks) throw std::bad_alloc();
            if (used == blocks.size()) {
                if (used == blocks.capacity()) blocks.reserve(std::min(max_blocks, std::max(size_t(4), used * 2)));
                blocks.emplace_back();
            }
            blocks[used].key = key;
            blocks[used].live = 0;
            slot = uint32_t(++used);
        }
        Block& block = blocks[slot - 1];
        const unsigned i = offset & 31;
        const uint32_t bit = uint32_t(1) << i;
        Quad& q = block.quads[i];
        if (!(block.live & bit)) {
            block.live |= bit;
            q.fill(Color{background[0], background[1], background[2], 255});
        }
        return q;
    }
public:
    void release() noexcept {
        forget();
        std::vector<Block>().swap(blocks);
        std::vector<uint32_t>().swap(slots);
    }
    bool begin(bool enable, uint8_t* indexed, size_t stride, int w, int h) {
        forget();
        if (!enable || w <= 0 || h <= 0 || stride < size_t(w) || stride > (4u << 20) / size_t(h)) {
            if (!slots.empty() || !blocks.empty()) release();
            return false;
        }
        pitch = stride;
        extent = pitch * size_t(h);
        width = w; height = h;
        try { slots.resize((extent + 31) / 32, 0); }
        catch (const std::bad_alloc&) { release(); return false; }
        base = indexed;
        return true;
    }
    bool active() const { return base != nullptr; }

    // A horizontal indexed write can also target GRP scratch, minimap data or
    // a clipped span. Only writes inside the currently observed plane matter.
    void retire(const uint8_t* dest, size_t count = 1) {
        if (!used || !count) return;
        const uintptr_t offset = reinterpret_cast<uintptr_t>(dest) - reinterpret_cast<uintptr_t>(base);
        if (offset >= extent) return;
        size_t at = offset;
        const size_t end = at + std::min(count, extent - at);
        while (at < end) {
            const unsigned first = at & 31;
            const unsigned n = unsigned(std::min(end-at, size_t(32-first)));
            const uint32_t slot = slots[at >> 5];
            if (slot) blocks[slot - 1].live &= ~((0xffffffffu >> (32 - n)) << first);
            at += n;
        }
    }
    void retire_xy(int x, int y, size_t count = 1) {
        if (used && x >= 0 && y >= 0 && x < width && y < height) retire(base + size_t(y)*pitch+x, count);
    }
    void fog(int x, int y, int light) {
        if (!used) return;
        const size_t offset = size_t(y)*pitch+x;
        auto* q = covered(offset);
        if (!q) return;
        if (light <= 0) { retire(base+offset); return; }
        for (auto& color : *q) for (int c = 0; c < 3; ++c)
            color[c] = uint8_t((unsigned(color[c]) * light + 15) / 31);
    }
    bool draw(const Pose& pose, int cx, int cy, bool flip, const uint8_t* tint,
              const Palette& palette, const uint8_t* wpe, uint8_t* indexed, bool shadow = false) {
        if (!active()) return false;
        int x0 = width, y0 = height, x1 = -1, y1 = -1;
        try {
            for_each_texel(pose, cx, cy, flip, tint, width*2, height*2,
                [&](int dx, int dy, uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
                    if (shadow) {
                        // Modifier 10 uses coverage only. Full silhouette opacity
                        // darkens the destination by 47% (classic dark.pcx row 18).
                        a = uint8_t((unsigned(a) * 120 + 127) / 255);
                        if (!a) return;
                        r = g = b = 0;
                    }
                    const int x = dx >> 1, y = dy >> 1;
                    const size_t i = size_t(y)*pitch + x;
                    auto& q = acquire(i, wpe + size_t(indexed[i])*4);
                    auto& dst = q[((dy & 1) << 1) | (dx & 1)];
                    if (a == 255) dst = {r,g,b,255};
                    else {
                        const unsigned inv = 255 - a;
                        dst = {uint8_t((unsigned(r)*a + dst[0]*inv + 127)/255),
                               uint8_t((unsigned(g)*a + dst[1]*inv + 127)/255),
                               uint8_t((unsigned(b)*a + dst[2]*inv + 127)/255),255};
                    }
                    x0 = std::min(x0,x); x1 = std::max(x1,x);
                    y0 = std::min(y0,y); y1 = std::max(y1,y);
                });
        } catch (const std::bad_alloc&) {
            // Earlier bodies already have an indexed representation. Abandon
            // this frame's extra samples and let the caller draw this pose at 1x.
            release(); return false;
        }
        // Classic background-reading effects still consume their original
        // indexed input, including the art underneath them. Only covered
        // effect pixels retire detail; untouched neighbors retain native RGB.
        for (int y = y0; y <= y1; ++y) for (int x = x0; x <= x1; ++x) {
            const size_t i = size_t(y)*pitch+x;
            if (auto* q = covered(i)) {
                unsigned rgb[3] = {};
                for (const auto& c : *q) for (int k=0;k<3;++k) rgb[k] += c[k];
                indexed[i] = palette.index((rgb[0]+2)/4,(rgb[1]+2)/4,(rgb[2]+2)/4);
            }
        }
        return true;
    }
    void present(uint8_t* out, size_t stride, const uint8_t* classic, size_t classic_stride,
                 int w, int h) const {
        // Expand the already finished classic canvas (including native UI),
        // then replace only the samples still owned by the ordered HD pass.
        for (int y=0; y<h; ++y) {
            const uint8_t* src = classic + size_t(y)*classic_stride;
            uint8_t* row = out + size_t(y)*2*stride;
            for (int x=0;x<w;++x) {
                std::memcpy(row+size_t(x)*8,src+size_t(x)*4,4);
                std::memcpy(row+size_t(x)*8+4,src+size_t(x)*4,4);
            }
            std::memcpy(row+stride,row,size_t(w)*8);
        }
        if (w != width || h != height) return;
        for (size_t block_i=0; block_i<used; ++block_i) {
            const auto& block = blocks[block_i];
            uint32_t bits = block.live;
            while (bits) {
                const unsigned j = unsigned(__builtin_ctz(bits));
                bits &= bits - 1;
                const size_t offset = block.key*32 + j;
                const size_t y = offset/pitch, x = offset - y*pitch;
                uint8_t* dst = out + y*2*stride + x*8;
                const auto& q = block.quads[j];
                std::memcpy(dst,q.data(),8);
                std::memcpy(dst+stride,q.data()+2,8);
            }
        }
    }
};

// Borrowed only during one ordered world rendering call. Scratch GRP draws
// are guarded by their destination address. Always restore on error or return.
inline thread_local DetailPlane* observing = nullptr;
struct ObserveFrame {
    DetailPlane* previous;
    explicit ObserveFrame(DetailPlane* plane) : previous(observing) { observing = plane; }
    ~ObserveFrame() { observing = previous; }
};

}}}
#endif
