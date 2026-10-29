package org.openbw.androidtest;

import android.graphics.*;
import java.nio.charset.Charset;
import java.util.*;

/** Original PCX, GRP, FONT, BIN and Smacker presentation, drawn by Android Canvas. */
final class NativeArt {
  static final Charset CP = Charset.forName("windows-1252");

  static int u16(byte[] b, int p) {
    return (b[p] & 255) | (b[p + 1] & 255) << 8;
  }

  static int u32(byte[] b, int p) {
    return u16(b, p) | u16(b, p + 2) << 16;
  }

  static String str(byte[] b, int p) {
    if (p < 1 || p >= b.length) return "";
    int e = p;
    while (e < b.length && b[e] != 0) e++;
    return new String(b, p, e - p, GameLanguage.charset());
  }

  static String clean(String s) {
    return s.replaceAll("[\\x00-\\x09\\x0b-\\x1f]", "");
  }

  static String normal(String s) {
    return s.replace('\\', '/').toLowerCase(Locale.ROOT);
  }

  static String[] tbl(byte[] b) {
    if (b == null || b.length < 2) return new String[0];
    int count = u16(b, 0);
    if (2 + 2 * count > b.length) throw new IllegalArgumentException("Invalid string table");
    String[] out = new String[count];
    for (int i = 0; i < count; i++) out[i] = str(b, u16(b, 2 + i * 2));
    return out;
  }

  static final Paint PIXELS = new Paint();
  static { setSmoothGraphics(true); }

  // One sampling policy for the battlefield and every bitmap in the interface.
  // NativeSurface changes it on the renderer thread before loading/drawing art.
  static void setSmoothGraphics(boolean enabled) {
    PIXELS.setFilterBitmap(enabled);
    PIXELS.setDither(false);
  }

  // Animated menu layers and portrait masks have their own alpha/color/blend
  // effects. Copy only sampling flags, preserving those presentation effects.
  static Paint bitmapPaint(Paint paint) {
    paint.setFilterBitmap(PIXELS.isFilterBitmap());
    paint.setDither(false);
    return paint;
  }

  static final class PCX {
    Bitmap image;
    byte[] pixels;
    int[] palette = new int[256];
    int width, height, stride;

    PCX(byte[] b, boolean transparent, int glass) {
      if (b == null || b.length < 897 || b[0] != 10 || (b[2] != 0 && b[2] != 1) || b[3] != 8 || b[65] != 1)
        throw new IllegalArgumentException("Missing or invalid PCX");
      width = u16(b, 8) - u16(b, 4) + 1;
      height = u16(b, 10) - u16(b, 6) + 1;
      stride = u16(b, 66);
      if (width < 1
          || height < 1
          || width > 2048
          || height > 2048
          || stride < width
          || stride > 4096) throw new IllegalArgumentException("Invalid PCX dimensions");
      pixels = new byte[stride * height];
      for (int i = 0; i < 256; i++) {
        int p = b.length - 768 + i * 3;
        palette[i] = Color.rgb(b[p] & 255, b[p + 1] & 255, b[p + 2] & 255);
      }
      int p = 128, o = 0;
      if (b[2] == 0) {
        if (pixels.length > b.length - 897) throw new IllegalArgumentException("Truncated uncompressed PCX");
        System.arraycopy(b, p, pixels, 0, pixels.length);
        o = pixels.length;
      }
      while (o < pixels.length) {
        if (p >= b.length - 769) throw new IllegalArgumentException("Truncated PCX");
        int v = b[p++] & 255, n = (v & 192) == 192 ? v & 63 : 1;
        if ((v & 192) == 192) {
          if (p >= b.length - 769) throw new IllegalArgumentException("Truncated PCX run");
          v = b[p++] & 255;
        }
        if (n == 0 || o + n > pixels.length) throw new IllegalArgumentException("Invalid PCX run");
        Arrays.fill(pixels, o, o + n, (byte) v);
        o += n;
      }
      int[] out = new int[width * height];
      for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++) {
          int i = pixels[y * stride + x] & 255;
          out[y * width + x] =
              transparent && i == 0
                  ? 0
                  : i == glass ? (palette[i] & 0xffffff) | 0x99000000 : palette[i];
        }
      image = Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888);
    }
  }

  static Bitmap grp(byte[] b, int id, int[] palette, boolean raw, boolean centered) {
    if (b == null || b.length < 6 || id < 0 || id >= u16(b, 0)) return null;
    int at = 6 + id * 8;
    if (at + 8 > b.length) return null;
    int w = b[at + 2] & 255,
        h = b[at + 3] & 255,
        base = u32(b, at + 4),
        cw = centered ? u16(b, 2) : w,
        ch = centered ? u16(b, 4) : h;
    if (w == 0
        || h == 0
        || cw > 1024
        || ch > 768
        || w > cw
        || h > ch
        || base < 0
        || base >= b.length) return null;
    int[] out = new int[cw * ch];
    int ox = (cw - w) / 2, oy = (ch - h) / 2;
    if (raw) {
      if (base + (long) w * h > b.length) return null;
      for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
          int i = b[base + y * w + x] & 255;
          out[(y + oy) * cw + x + ox] = i == 0 ? 0 : palette[i];
        }
    } else {
      if (base + h * 2 > b.length) return null;
      for (int y = 0; y < h; y++) {
        int p = base + u16(b, base + y * 2), x = 0;
        while (x < w) {
          if (p >= b.length) return null;
          int v = b[p++] & 255, n = (v & 128) != 0 ? v & 127 : (v & 64) != 0 ? v & 63 : v;
          if (n == 0 || x + n > w) return null;
          if ((v & 128) != 0) {
            x += n;
            continue;
          }
          int repeat = -1;
          if ((v & 64) != 0) {
            if (p >= b.length) return null;
            repeat = b[p++] & 255;
          }
          for (int k = 0; k < n; k++, x++) {
            if (repeat < 0 && p >= b.length) return null;
            int i = repeat < 0 ? b[p++] & 255 : repeat;
            out[(y + oy) * cw + x + ox] = palette[i];
          }
        }
      }
    }
    return Bitmap.createBitmap(out, cw, ch, Bitmap.Config.ARGB_8888);
  }

  static final class Font {
    final byte[] data;
    final PCX tint;
    final int[] palette;
    final boolean game, fallback, wide;
    final Font substitute;
    final int low, high, height, spacing;
    final Map<Integer, Bitmap> glyphs = new HashMap<Integer, Bitmap>();
    private boolean glyphsSmooth = PIXELS.isFilterBitmap();
    int fixedColor, shadedColor;
    private SystemFont systemFont;

    Font withShadedColor(int color) {
      if (substitute != null) return new Font(substitute.withShadedColor(color), height, spacing);
      Font copy = new Font(fallback ? null : data, tint, palette, spacing, height, wide);
      copy.shadedColor = color;
      return copy;
    }

    Font withColor(int color) {
      if (substitute != null) return new Font(substitute.withColor(color), height, spacing);
      Font copy = new Font(fallback ? null : data, tint, palette, spacing, height, wide);
      copy.fixedColor = color;
      return copy;
    }

    Font(byte[] bytes, PCX tint, int[] palette, int spacing, int size) {
      this(bytes, tint, palette, spacing, size, false);
    }

    Font(byte[] bytes, PCX tint, int[] palette, int spacing, int size, boolean wide) {
      this.wide = wide;
      substitute = null;
      fallback = bytes == null;
      if (fallback) {
        bytes = new byte[12];
        System.arraycopy("FONT".getBytes(CP), 0, bytes, 0, 4);
        bytes[6] = (byte) (size * .65);
        bytes[7] = (byte) size;
      }
      if (bytes == null || bytes.length < 8 || !new String(bytes, 0, 4, CP).equals("FONT"))
        throw new IllegalArgumentException("Original font is missing");
      data = bytes;
      low = data[4] & 255;
      high = data[5] & 255;
      height = data[7] & 255;
      if (high < low || height < 1 || height > 64 || 8 + 4 * (high - low + 1) > data.length)
        throw new IllegalArgumentException("Invalid FONT");
      this.tint = tint;
      this.palette = palette;
      this.spacing = spacing;
      game = tint.stride >= 64 && tint.pixels.length >= 192;
    }

    // Keep the available original letterforms and color ramps when only a
    // different size exists. The normal renderer still owns layout/wrapping.
    Font(Font source, int height, int spacing) {
      substitute = source;
      data = source.data;
      tint = source.tint;
      palette = source.palette;
      low = source.low;
      high = source.high;
      this.height = height;
      this.spacing = spacing;
      game = source.game;
      wide = source.wide;
      fallback = false;
      fixedColor = source.fixedColor;
      shadedColor = source.shadedColor;
    }

    int[] ramp(int c) {
      if (!game) return new int[] {0, c >= 2 && c <= 6 ? c - 2 : 0};
      switch (c) {
        case 3:
          return new int[] {0, 1};
        case 4:
          return new int[] {0, 2};
        case 5:
          return new int[] {0, 3};
        case 6:
          return new int[] {0, 4};
        case 7:
          return new int[] {0, 5};
        case 8:
          return new int[] {1, 0};
        case 11:
          return new int[] {0, 6};
        case 14:
          return new int[] {1, 1};
        case 15:
          return new int[] {1, 2};
        case 16:
          return new int[] {1, 3};
        case 17:
          return new int[] {1, 4};
        case 20:
          return new int[] {0, 7};
        case 21:
          return new int[] {1, 5};
        case 22:
          return new int[] {1, 6};
        case 23:
          return new int[] {1, 7};
        case 24:
          return new int[] {2, 0};
        case 25:
          return new int[] {2, 1};
        case 27:
          return new int[] {2, 2};
        case 28:
          return new int[] {2, 3};
        case 29:
          return new int[] {2, 4};
        case 30:
          return new int[] {2, 5};
        case 31:
          return new int[] {2, 7};
        default:
          return new int[] {0, 0};
      }
    }

    Bitmap glyph(int code, int color) {
      if (substitute != null && glyphsSmooth != PIXELS.isFilterBitmap()) {
        // Resized substitute fonts must not retain the previous sampling mode.
        glyphs.clear();
        glyphsSmooth = PIXELS.isFilterBitmap();
      }
      int key = (code & 65535) | color << 16;
      if (glyphs.containsKey(key)) return glyphs.get(key);
      if (substitute != null) {
        Bitmap original = substitute.glyph(code, color);
        if (original == null) return null;
        int w = Math.max(1, Math.round((original.getWidth() - 1) * height / (float) substitute.height) + 1);
        Bitmap scaled = Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888);
        new Canvas(scaled).drawBitmap(original, null, new RectF(0, 0, w, height), PIXELS);
        glyphs.put(key, scaled);
        return scaled;
      }
      int nativeCode = GameLanguage.glyphCode(code);
      int nativeOffset = nativeCode >= low && nativeCode <= high ? u32(data, 8 + (nativeCode - low) * 4) : 0;
      if (fallback || nativeOffset < 1 || nativeOffset + 4 > data.length) {
        int[] r = ramp(color), shades = new int[4];
        for (int i = 0; i < shades.length; i++) {
          int index = r[0] * tint.stride + r[1] * 8 + i + 1;
          int rgb = index < tint.pixels.length ? palette[tint.pixels[index] & 255] : 0xffcbd3e8;
          if (Color.red(rgb) >= 248 && Color.green(rgb) == 0 && Color.blue(rgb) >= 248)
            rgb = i == 0 ? 0xffcbd3e8 : shades[i - 1];
          if (shadedColor != 0) {
            int light = Math.max(Color.red(rgb), Math.max(Color.green(rgb), Color.blue(rgb)));
            rgb = Color.rgb(Color.red(shadedColor) * light / 255,
                Color.green(shadedColor) * light / 255, Color.blue(shadedColor) * light / 255);
          }
          shades[i] = fixedColor != 0 ? fixedColor : rgb;
        }
        Bitmap bitmap = systemFont().glyph(code, shades);
        glyphs.put(key, bitmap);
        return bitmap;
      }
      int p = nativeOffset;
      if (p < 1 || p + 4 > data.length) return null;
      int w = data[p++] & 255, h = data[p++] & 255, ox = data[p++] & 255, oy = data[p++] & 255;
      if (w > 64 || h > 64 || ox + w > 96 || oy + h > height || w == 0) return null;
      int cw = Math.max(1, w + ox + 1), n = 0;
      int[] out = new int[cw * height], r = ramp(color);
      while (n < w * h && p < data.length) {
        int b = data[p++] & 255;
        n += b >> 3;
        if (n >= w * h) break;
        int col = b & 7;
        if (col != 0) {
          int index = tint.pixels[r[0] * tint.stride + r[1] * 8 + col] & 255, rgb = palette[index];
          boolean transparent = index == 0 || (Color.red(rgb) >= 248 && Color.green(rgb) == 0 && Color.blue(rgb) >= 248);
          if (shadedColor != 0 && !transparent) {
            int shade = Math.max(Color.red(rgb), Math.max(Color.green(rgb), Color.blue(rgb)));
            rgb = Color.rgb(Color.red(shadedColor) * shade / 255,
                Color.green(shadedColor) * shade / 255, Color.blue(shadedColor) * shade / 255);
          }
          if (!transparent)
            out[(n / w + oy) * cw + n % w + ox] =
                fixedColor != 0 && (rgb & 0xffffff) != 0 ? fixedColor : rgb;
          n++;
        }
        // A zero-colour byte is a transparent run only. It does not emit an
        // extra pixel. Localized fonts use these runs between every row.
      }
      Bitmap image = Bitmap.createBitmap(out, cw, height, Bitmap.Config.ARGB_8888);
      glyphs.put(key, image);
      return image;
    }

    int advance(int c) {
      if (substitute != null) {
        if (c == 32 || c == 9) return Math.max(1, Math.round(substitute.advance(c) * height / (float) substitute.height));
        if (c < 32) return 0;
        Bitmap b = glyph(c, 2);
        return b == null ? 0 : b.getWidth() - 1 + spacing;
      }
      if (fallback) {
        if (c == 9) return advance(32) * 4;
        if (c < 32) return 0;
        return systemFont().advance(c);
      }
      if (c == 32) return (data[6] & 255) / 2;
      if (c == 9) return (data[6] & 255) * 2;
      if (c < 32) return 0;
      Bitmap b = glyph(c, 2);
      return b == null ? 0 : b.getWidth() - 1 + spacing;
    }

    private SystemFont systemFont() {
      if (systemFont == null) systemFont = new SystemFont(height, spacing, wide);
      return systemFont;
    }

    int measure(String s) {
      s = PortText.text(s);
      int w = 0;
      for (int i = 0; i < s.length(); i++) w += advance(s.charAt(i));
      return w;
    }

    List<String> lines(String s, int width) {
      s = PortText.text(s);
      ArrayList<String> lines = new ArrayList<String>();
      StringBuilder line = new StringBuilder();
      int x = 0;
      for (String token : s.replace("\r", "").split("(?<=\\n)|(?=\\n)|(?<= )")) {
        if (token.equals("\n")) {
          lines.add(line.toString());
          line.setLength(0);
          x = 0;
          continue;
        }
        int w = measure(token);
        if (width > 0 && x > 0 && x + w > width) {
          lines.add(line.toString().replaceAll(" +$", ""));
          line.setLength(0);
          x = 0;
        }
        // A long name or untranslated token still has to stay within its box.
        if(width>0&&w>width){for(int i=0;i<token.length();i++){char ch=token.charAt(i);int a=advance(ch);if(x>0&&x+a>width){lines.add(line.toString());line.setLength(0);x=0;}line.append(ch);x+=a;}}
        else {line.append(token);x+=w;}
      }
      lines.add(line.toString().replaceAll(" +$", ""));
      return lines;
    }

    float draw(
        Canvas c,
        String s,
        float left,
        float top,
        int width,
        int align,
        int color,
        int lineHeight) {
      int current = color;
      boolean hidden = false;
      for (String line : lines(s, width)) {
        int a = line.indexOf('\u0013') >= 0 ? 1 : line.indexOf('\u0012') >= 0 ? 2 : align;
        float x =
            left + (a == 1 ? (width - measure(line)) / 2f : a == 2 ? width - measure(line) : 0);
        for (int i = 0; i < line.length(); i++) {
          int code = line.charAt(i);
          if (code < 32) {
            if (code == 1) {
              current = color;
              hidden = false;
            } else if (code == 9) x += advance(code);
            else if (code == 12) hidden = true;
            else if (code != 18 && code != 19) {
              current = code;
              hidden = false;
            }
            continue;
          }
          Bitmap b = glyph(code, current);
          if (b != null && !hidden) c.drawBitmap(b, x, top, PIXELS);
          x += advance(code);
        }
        top += lineHeight > 0 ? Math.max(lineHeight,height+1) : height;
      }
      return top;
    }
  }

  /** Rasterize the installed Android sans font once, then use the menu's own
   * highlight/face/shadow ramp. No external or bundled game font is needed. */
  private static final class SystemFont {
    final int height, spacing, baseline;
    final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Map<Integer, Mask> masks = new HashMap<Integer, Mask>();

    static final class Mask {
      int width, advance;
      int[] pixels;
    }

    SystemFont(int height, int spacing, boolean wide) {
      this.height = height;
      this.spacing = spacing;
      ink.setTypeface(Typeface.create("sans-serif", height >= 14 ? Typeface.BOLD : Typeface.NORMAL));
      ink.setTextScaleX(wide ? 1.15f : 1f);
      ink.setTextSize(height);
      // Size from visible letters, not platform-dependent line padding.
      Rect bounds = new Rect();
      ink.getTextBounds("Hg", 0, 2, bounds);
      if (bounds.height() > height - 2)
        ink.setTextSize(height * Math.max(1, height - 2) / (float) bounds.height());
      ink.getTextBounds("Hg", 0, 2, bounds);
      baseline = Math.max(0, (height - 1 - bounds.height()) / 2) - bounds.top;
      ink.setColor(Color.WHITE);
    }

    Mask mask(int code) {
      Mask cached = masks.get(code);
      if (cached != null) return cached;
      String letter = Character.toString((char) code);
      Rect bounds = new Rect();
      float size = ink.getTextSize();
      ink.getTextBounds(letter, 0, 1, bounds);
      if (bounds.height() > height - 1) {
        ink.setTextSize(size * Math.max(1, height - 1) / (float) bounds.height());
        ink.getTextBounds(letter, 0, 1, bounds);
      }
      int left = Math.max(0, -bounds.left);
      int body = Math.max(Math.round(ink.measureText(letter)), bounds.right + left);
      Mask result = new Mask();
      result.advance = Math.max(1, body + (code == 32 ? 0 : spacing));
      result.width = Math.max(result.advance, body + 1);
      Bitmap mask = Bitmap.createBitmap(result.width, height, Bitmap.Config.ARGB_8888);
      float y = Math.max(-bounds.top, Math.min(baseline, height - 1 - bounds.bottom));
      new Canvas(mask).drawText(letter, left, y, ink);
      result.pixels = new int[result.width * height];
      mask.getPixels(result.pixels, 0, result.width, 0, 0, result.width, height);
      mask.recycle();
      ink.setTextSize(size);
      masks.put(code, result);
      return result;
    }

    int advance(int code) { return mask(code).advance; }

    Bitmap glyph(int code, int[] shades) {
      Mask mask = mask(code);
      int w = mask.width;
      int[] out = new int[w * height];
      for (int y = 0; y < height - 1; y++) for (int x = 0; x < w - 1; x++) {
        int alpha = mask.pixels[y * w + x] >>> 24;
        if (alpha != 0) out[(y + 1) * w + x + 1] = (alpha * 3 / 4 << 24) | (shades[3] & 0xffffff);
      }
      for (int y = 0; y < height; y++) for (int x = 0; x < w; x++) {
        int alpha = mask.pixels[y * w + x] >>> 24;
        if (alpha == 0) continue;
        int above = y == 0 ? 0 : mask.pixels[(y - 1) * w + x] >>> 24;
        int below = y == height - 1 ? 0 : mask.pixels[(y + 1) * w + x] >>> 24;
        int shade = height < 12 || above < 96 ? 0 : below < 96 ? 2 : 1;
        out[y * w + x] = (alpha << 24) | (shades[shade] & 0xffffff);
      }
      return Bitmap.createBitmap(out, w, height, Bitmap.Config.ARGB_8888);
    }
  }

  static final class Widget {
    int id, type, x, y, w, h, flags, smk, tx, ty, rx, ry, rx2, ry2, rw, rh;
    String text;

    RectF rect() {
      return new RectF(x, y, x + w, y + h);
    }

    RectF hit() {
      return (flags & 16) != 0 && rx2 > rx && ry2 > ry
          ? new RectF(x + rx, y + ry, x + rx2 + 1, y + ry2 + 1)
          : rect();
    }
  }

  static final class Anim {
    String name;
    int flags, x, y;
  }

  static final class Dialog {
    final byte[] b;
    final List<Widget> widgets = new ArrayList<Widget>();

    Dialog(byte[] b) {
      if (b == null || b.length < 86) throw new IllegalArgumentException("Missing original dialog");
      this.b = b;
      Set<Integer> seen = new HashSet<Integer>();
      int p = u32(b, 66);
      while (p > 0) {
        if (p > b.length - 86 || !seen.add(p) || seen.size() > 512)
          throw new IllegalArgumentException("Invalid dialog chain");
        Widget w = new Widget();
        w.x = u16(b, p + 4);
        w.y = u16(b, p + 6);
        w.w = u16(b, p + 12);
        w.h = u16(b, p + 14);
        w.text = str(b, u32(b, p + 20));
        w.flags = u32(b, p + 24);
        w.id = u16(b, p + 32);
        w.type = u32(b, p + 34);
        w.smk = u32(b, p + 66);
        w.tx = u16(b, p + 70);
        w.ty = u16(b, p + 72);
        w.rx = u16(b, p + 54);
        w.ry = u16(b, p + 56);
        w.rx2 = u16(b, p + 58);
        w.ry2 = u16(b, p + 60);
        w.rw = u16(b, p + 74);
        w.rh = u16(b, p + 76);
        widgets.add(w);
        p = u32(b, p);
      }
      if (p < 0 || widgets.isEmpty()) throw new IllegalArgumentException("Invalid or empty dialog");
    }

    Widget get(int id) {
      for (Widget w : widgets) if (w.id == id) return w;
      return null;
    }

    List<Anim> animations(Widget w) {
      ArrayList<Anim> list = new ArrayList<Anim>();
      Set<Integer> seen = new HashSet<Integer>();
      int p = w.smk;
      while (p > 0) {
        if (p > b.length - 30 || !seen.add(p)) break;
        Anim a = new Anim();
        a.name = normal(str(b, u32(b, p + 10)));
        a.flags = u16(b, p + 4);
        a.x = (short) u16(b, p + 18);
        a.y = (short) u16(b, p + 20);
        list.add(a);
        p = u32(b, p);
      }
      Collections.reverse(list);
      return list;
    }
  }

  static final class Movie implements AutoCloseable {
    final int id, frames, interval;
    final Bitmap bitmap;
    int frame = -1;

    Movie(byte[] data) {
      this(data, false);
    }

    Movie(byte[] data, boolean audio) {
      this(NativeBridge.movieOpen(data, audio));
    }

    Movie(int slot, String path) {this(NativeBridge.movieOpenAsset(slot, path, true));}

    private Movie(int[] m) {
      id = m[0];
      frames = m[3];
      interval = Math.max(1, m[4]);
      bitmap = Bitmap.createBitmap(m[1], m[2], Bitmap.Config.ARGB_8888);
    }

    void draw(
        Canvas c,
        float x,
        float y,
        float width,
        float height,
        double time,
        boolean loop,
        Paint paint) {
      int f = (int) (Math.max(0, time) / interval);
      f = loop ? f % frames : Math.min(f, frames - 1);
      if (f != frame) {
        NativeBridge.movieFrame(id, time, loop, bitmap);
        frame = f;
      }
      c.drawBitmap(bitmap, null, new RectF(x, y, x + width, y + height), bitmapPaint(paint));
    }

    public void close() {
      NativeBridge.movieClose(id);
    }
  }

  final Map<String, byte[]> bytes = new HashMap<String, byte[]>();
  final Map<String, PCX> images = new HashMap<String, PCX>();
  final Map<String, byte[]> fonts = new HashMap<String, byte[]>();
  private final Map<String, String> fontEncodings = new HashMap<String, String>();
  private final Set<String> unavailableFonts = new HashSet<String>();
  private final Map<String, String> rejected = new LinkedHashMap<String, String>();
  private final Map<String, String> selected = new LinkedHashMap<String, String>();
  private final Map<String, String> fontStatus = new LinkedHashMap<String, String>();
  private final Set<String> missing = new LinkedHashSet<String>();
  private static final int[] SOURCES = {2, 1, 0, 4};
  private static final String[] SOURCE_NAMES = {"StarDat.mpq", "BrooDat.mpq", "Patch_rt.mpq", "map", "imported interface"};

  private interface Decoder<T> { T decode(int source, byte[] data); }

  // A patch can contain an unreadable entry while a lower-priority archive has
  // a usable copy. Validate each candidate before committing to that source.
  private <T> T decode(String name, Decoder<T> decoder) {
    String n = normal(name);
    for (int slot : SOURCES) {
      try {
        byte[] data = NativeBridge.asset(slot, n);
        if (data == null || data.length == 0) continue;
        T decoded = decoder.decode(slot, data);
        if (selected.size() < 96 || selected.containsKey(n))
          selected.put(n, SOURCE_NAMES[slot] + " (" + data.length + " bytes)");
        missing.remove(n);
        return decoded;
      } catch (RuntimeException error) {
        String key = SOURCE_NAMES[slot] + ": " + n;
        if (rejected.size() < 16 || rejected.containsKey(key))
          rejected.put(key, error.getClass().getSimpleName() + ": " + error.getMessage());
      }
    }
    if (missing.size() < 48) missing.add(n);
    throw new IllegalArgumentException("Missing or unreadable asset: " + n);
  }

  String diagnostics() {
    StringBuilder result = new StringBuilder();
    result.append("Font choices:\n");
    for (Map.Entry<String, String> entry : fontStatus.entrySet())
      result.append("font").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
    result.append("Loaded assets:\n");
    for (Map.Entry<String, String> entry : selected.entrySet())
      result.append(entry.getKey()).append(" <- ").append(entry.getValue()).append('\n');
    result.append("Missing/unreadable candidates (another path may have loaded):\n");
    for (String path : missing) result.append(path).append('\n');
    result.append("Rejected candidates:\n");
    for (Map.Entry<String, String> entry : rejected.entrySet())
      result.append(entry.getKey()).append(" — ").append(entry.getValue()).append('\n');
    return result.toString();
  }

  Dialog dialog(String name) { return decode("rez/" + name + ".bin", (slot, data) -> new Dialog(data)); }

  byte[] read(String name) {
    String n = normal(name);
    if (bytes.containsKey(n)) return bytes.get(n);
    byte[] b;
    try { b = decode(n, (slot, data) -> data); }
    catch (IllegalArgumentException missing) { return null; }
    if (b != null && b.length < 1024 * 1024) bytes.put(n, b);
    return b;
  }

  PCX pcx(String name, boolean alpha, int glass) {
    String k = name + ":" + alpha + ":" + glass;
    if (images.containsKey(k)) return images.get(k);
    PCX p = decode(name, (slot, data) -> new PCX(data, alpha, glass));
    images.put(k, p);
    return p;
  }

  Font font(String size, PCX tint, int[] pal, int spacing) {
    int height = Integer.parseInt(size.replace("x", ""));
    Font exact = originalFont(size, tint, pal, spacing);
    if (exact != null) {
      fontStatus.put(size, "ORIGINAL: font" + size + ".fnt; " + fontSource(size));
      return exact;
    }
    List<String> alternatives = new ArrayList<String>(Arrays.asList("8", "10", "12", "14", "16", "16x", "32", "50"));
    alternatives.remove(size);
    Collections.sort(alternatives, (a, b) -> Double.compare(fontDistance(a, size), fontDistance(b, size)));
    for (String candidate : alternatives) {
      Font original = originalFont(candidate, tint, pal, spacing);
      if (original != null) {
        fontStatus.put(size, "ORIGINAL SUBSTITUTE: font" + candidate + ".fnt scaled to " + height
            + " px; " + fontSource(candidate));
        return new Font(original, height, spacing);
      }
    }
    fontStatus.put(size, "STYLED SYSTEM FALLBACK: Android sans-serif "
        + (height >= 14 ? "bold" : "regular") + (size.endsWith("x") ? ", wide buttons" : "")
        + ", menu palette shading; no usable original font in either path at any supported size");
    return new Font(null, tint, pal, spacing, height, size.endsWith("x"));
  }

  private static double fontDistance(String candidate, String requested) {
    int actual = Integer.parseInt(candidate.replace("x", ""));
    int desired = Integer.parseInt(requested.replace("x", ""));
    return Math.abs(Math.log(actual / (double) desired))
        + (candidate.endsWith("x") == requested.endsWith("x") ? 0 : .05);
  }

  private String fontSource(String size) {
    for (String prefix : new String[]{"files/font/", "font/"}) {
      String path = prefix + "font" + size + ".fnt";
      if (selected.containsKey(path)) return path + " <- " + selected.get(path)
          + "; " + fontEncodings.get(size);
    }
    return "validated original font";
  }

  private Font originalFont(String size, PCX tint, int[] pal, int spacing) {
    if (unavailableFonts.contains(size)) return null;
    int height = Integer.parseInt(size.replace("x", ""));
    byte[] b = fonts.get(size);
    if (b != null) return new Font(b, tint, pal, spacing, height, size.endsWith("x"));
    for (String prefix : GameLanguage.russianData ? new String[]{"font/", "files/font/"} : new String[]{"files/font/", "font/"}) {
      try {
        Font font = decode(prefix + "font" + size + ".fnt", (slot, data) -> {
          byte[] readable = fontBytes(slot, data);
          Font decoded = new Font(readable, tint, pal, spacing, height, size.endsWith("x"));
          fontEncodings.put(size, readable == data ? "plain FONT" : "installed font decrypted; checksum verified");
          return decoded;
        });
        fonts.put(size, font.data);
        return font;
      } catch (RuntimeException unavailableFont) {
        // Installed archives may contain unreadable font payloads. Try the
        // next original source, then use the readable UI fallback.
      }
    }
    unavailableFonts.add(size);
    return null;
  }

  private byte[] fontBytes(int source, byte[] data) {
    if (ClassicFontCipher.isPlain(data)) return data;
    if (!ClassicFontCipher.isWrapped(data))
      throw new IllegalArgumentException("Unsupported font data; original file exists");
    String failure = "Encrypted font: installation metadata unavailable";
    // Prefer metadata from the font's own archive; an installed patch can use
    // the base game's metadata. Neither metadata nor its contents are logged.
    for (int slot : source == 0 ? new int[]{0} : new int[]{source, 0}) {
      byte[] metadata = null;
      try {
        byte[] stored = NativeBridge.asset(slot, "font/font.gid");
        if (stored == null) continue;
        metadata = stored.clone();
        return ClassicFontCipher.decodeFont(data, metadata);
      } catch (RuntimeException invalid) {
        failure = "Encrypted font: installed metadata or font checksum could not be validated";
      } finally {
        if (metadata != null) Arrays.fill(metadata, (byte) 0);
      }
    }
    throw new IllegalArgumentException(failure);
  }

  void clearImages() {
    images.clear();
    bytes.clear();
  }
}
