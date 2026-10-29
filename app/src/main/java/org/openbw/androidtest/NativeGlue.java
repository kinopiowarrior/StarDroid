package org.openbw.androidtest;

import android.graphics.*;
import java.util.*;

/** Original glue screen artwork and time-based menu movement. */
final class NativeGlue implements AutoCloseable {
  final NativeArt art;
  final NativeArt.Dialog layout;
  final NativeArt.PCX background, tint;
  final NativeArt.Font tiny, small, large, button, dialogue;
  final String name;
  final String paletteName;
  final String recovery;
  final String artworkRecovery;
  final Map<Integer, NativeArt.PCX> panels = new HashMap<Integer, NativeArt.PCX>();
  final Map<Integer, Bitmap> fixedPanelDecorations = new HashMap<Integer, Bitmap>();
  final Map<Integer, Bitmap> movingPanels = new HashMap<Integer, Bitmap>();
  final Set<Integer> scaledPanels = new HashSet<Integer>();

  static final class Layer {
    NativeArt.Widget widget;
    NativeArt.Anim spec;
    NativeArt.Movie movie;
    double hotTime;
    boolean wasHot;
  }

  final List<Layer> layers = new ArrayList<Layer>();
  final Paint paint = new Paint();
  private final Paint scrollbarPaint = new Paint();
  final Map<String, Bitmap> selectors = new HashMap<String, Bitmap>();
  final Bitmap[] portraitMasks = new Bitmap[4], portraitFrames = new Bitmap[4], speakerFrames = new Bitmap[4];
  final Paint maskPaint = new Paint();
  static final int ENTER_MS = 670, EXIT_MS = 365;
  double elapsed = 0, animationElapsed = 0;
  boolean slide;
  boolean exiting;
  boolean selectedStart;
  int hot = -1;

  NativeGlue(NativeArt art, String bin, String pal, boolean slide) {
    this.art = art;
    name = bin;
    this.slide = slide;
    layout = art.dialog(bin);
    NativeArt.PCX bg, colors;
    String issue = "", actualPalette = pal;
    try {
      bg = art.pcx("glue/" + pal + "/backgnd.pcx", false, -1);
      colors = art.pcx("glue/" + pal + "/tfont.pcx", false, -1);
    } catch (RuntimeException missingPalette) {
      // Some classic installation-media variants provide the fonts but omit
      // palcs. Preserve the dialog/actions with the core archive's neutral UI.
      if (pal.equals("palnl")) throw missingPalette;
      issue = missingPalette.getMessage();
      actualPalette = "palnl";
      bg = art.pcx("glue/palnl/backgnd.pcx", false, -1);
      colors = art.pcx("glue/palnl/tfont.pcx", false, -1);
    }
    paletteName = actualPalette;
    artworkRecovery = issue;
    background = bg;
    tint = colors;
    small = art.font("10", tint, background.palette, 1);
    tiny = art.font("8", tint, background.palette, 1);
    large = art.font("16", tint, background.palette, 1);
    dialogue = art.font("14", tint, background.palette, 1);
    NativeArt.Font alt;
    try {
      alt = art.font("16x", tint, background.palette, 1);
    } catch (Exception e) {
      alt = large;
    }
    button = alt;
    List<String> fallbackFonts = new ArrayList<String>();
    if (tiny.fallback) fallbackFonts.add("8");
    if (small.fallback) fallbackFonts.add("10");
    if (dialogue.fallback) fallbackFonts.add("14");
    if (large.fallback) fallbackFonts.add("16");
    if (button.fallback) fallbackFonts.add("16x");
    if (!fallbackFonts.isEmpty()) issue += (issue.isEmpty() ? "" : "\n")
        + "Original fonts unavailable: " + String.join(", ", fallbackFonts)
        + ". Styled Android font with menu palette shading is in use.";
    if (tiny.substitute != null || small.substitute != null || dialogue.substitute != null
        || large.substitute != null || button.substitute != null)
      issue += (issue.isEmpty() ? "" : "\n")
          + "Available original font sizes are reused for missing sizes. See Font choices below.";
    recovery = issue;
    int glass = 138;
    try {
      glass = art.pcx("glue/" + paletteName + "/teffect.pcx", false, -1).pixels[1] & 255;
    } catch (Exception ignored) {
    }
    for (NativeArt.Widget w : layout.widgets) {
      if (w.type == 5 && (w.flags & 8) != 0)
        try {
          panels.put(
              w.id, art.pcx(NativeArt.normal(w.text), true, (w.flags & 0x40000) != 0 ? glass : -1));
        } catch (Exception ignored) {
          // Core-only installs may lack the CD's custom-game panels. Keep the
          // original multiplayer frames until the optional interface import is present.
          if(name.equals("glucustm")&&(w.id==1||w.id==2||w.id==19))try {
            NativeArt.PCX original=art.pcx(w.id==2?"glue/chatroom/pjoin.pcx":"glue/chatroom/plist.pcx",true,glass);
            // Resize from the original at draw time so toggling smoothing also
            // updates an already open custom-game screen.
            panels.put(w.id,original);scaledPanels.add(w.id);
          }catch(Exception missing){}
        }
      List<NativeArt.Anim> animations = layout.animations(w);
      if (!name.startsWith("glurdy")) {
        Collections.reverse(animations);
        Collections.sort(animations, (a, b) -> Integer.compare(a.flags & 8, b.flags & 8));
      }
      for (NativeArt.Anim a : animations) {
        try {
          byte[] b = art.read(a.name);
          if (b == null) continue;
          Layer l = new Layer();
          l.widget = w;
          l.spec = a;
          l.movie = new NativeArt.Movie(b);
          layers.add(l);
        } catch (Exception ignored) {
        }
      }
    }
    maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    if (name.startsWith("glurdy")) {
      // Replay PCXs include a decorative support below the button frame.
      // Keep that support in the room; only the actual frame slides in/out.
      NativeArt.Widget replay = layout.get(5), replayLabel = layout.get(20);
      NativeArt.PCX replayArt = panels.get(5);
      if (!name.equals("glurdyp") && replay != null && replayLabel != null && replayArt != null) {
        int[] pixels = new int[replayArt.width * replayArt.height];
        replayArt.image.getPixels(pixels, 0, replayArt.width, 0, 0, replayArt.width, replayArt.height);
        int frameBottom = Math.min(replayArt.height, replayLabel.y + replayLabel.h + 8 - replay.y);
        int[] fixed = pixels.clone();
        Arrays.fill(fixed, 0, frameBottom * replayArt.width, 0);
        Arrays.fill(pixels, frameBottom * replayArt.width, pixels.length, 0);
        fixedPanelDecorations.put(5, Bitmap.createBitmap(fixed, replayArt.width, replayArt.height, Bitmap.Config.ARGB_8888));
        movingPanels.put(5, Bitmap.createBitmap(pixels, replayArt.width, replayArt.height, Bitmap.Config.ARGB_8888));
      }
      String letter = name.substring(name.length() - 1), prefix = "glue/ready" + letter + "/";
      for (int i = 0; i < 4; i++) try {
        NativeArt.Widget w = layout.get(15 + i), border = layout.get(9 + i);
        NativeArt.PCX panel = panels.get(9 + i);
        if (w != null && panel != null) {
          int[] pixels = new int[w.w * w.h];
          for (int y = 0; y < w.h; y++) for (int x = 0; x < w.w; x++) {
            int px = w.x + x - border.x, py = w.y + y - border.y;
            if (px >= 0 && py >= 0 && px < panel.width && py < panel.height
                && (panel.pixels[py * panel.stride + px] & 255) == glass) pixels[y * w.w + x] = Color.WHITE;
          }
          portraitMasks[i] = Bitmap.createBitmap(pixels, w.w, w.h, Bitmap.Config.ARGB_8888);
        }
        portraitFrames[i] = art.pcx(prefix + letter + "frame" + (i + 1) + ".pcx", true, -1).image;
        speakerFrames[i] = art.pcx(prefix + letter + "frameh" + (i + 1) + ".pcx", true, -1).image;
      } catch (Exception ignored) {}
      if (name.equals("glurdyt")) {
        Layer base = null;
        boolean hasSelected = false;
        for (Layer l : layers) {
          if (l.spec.name.endsWith("/start.smk")) base = l;
          if (l.spec.name.endsWith("/starton.smk")) hasSelected = true;
        }
        byte[] bytes = art.read(prefix + "starton.smk");
        if (!hasSelected && base != null && bytes != null) {
          Layer selected = new Layer(); selected.widget = base.widget;
          selected.spec = new NativeArt.Anim(); selected.spec.flags = 12;
          selected.spec.name = prefix + "starton.smk";
          selected.movie = new NativeArt.Movie(bytes);
          selected.spec.x = base.spec.x + (base.movie.bitmap.getWidth() - selected.movie.bitmap.getWidth()) / 2;
          selected.spec.y = base.spec.y + base.movie.bitmap.getHeight() - selected.movie.bitmap.getHeight();
          layers.add(selected);
        }
      }
      // Projector hardware belongs underneath its planet/crystal, including
      // the first frame of a hover transition.
      Collections.sort(layers, (a, b) -> Integer.compare(projector(a) ? 0 : 1, projector(b) ? 0 : 1));
    }
  }

  private static boolean projector(Layer l) {
    return l.spec.name.endsWith("/proj.smk") || l.spec.name.endsWith("/proloop.smk");
  }

  private double projectorIntro(Layer loop) {
    for (Layer l : layers)
      if (l.widget.id == loop.widget.id && l.spec.name.endsWith("/proj.smk"))
        return (double)l.movie.frames * l.movie.interval;
    return 0;
  }

  float slideDistance(NativeArt.Widget w) {
    if (!slide) return 0;
    if (name.equals("glucmpgn") || name.equals("gluexpcmpgn")) {
      if (w.id >= 6 && w.id <= 8) return 0;
      if (w.id == 5 || w.id == 11) w = layout.get(1);
      else if (w.id == 9 || w.id == 10) w = layout.get(3);
    } else if (name.equals("glucustm")) {
      if (w.id == 12) w = layout.get(4);
      else if (w.id == 13) w = layout.get(3);
      else if (w.id >= 20 && w.id <= 35) w = layout.get(19);
      else if (w.id == 7 || w.id == 8 || w.id == 10 || w.id == 11) w = layout.get(2);
      else if (w.type != 5) w = layout.get(1);
    } else if (name.equals("glulogin")) {
      if (w.id == 4) w = layout.get(3);
      else if (w.id == 5) w = layout.get(2);
      else if (w.id >= 6 && w.id <= 9) w = layout.get(1);
    } else if (name.startsWith("glurdy")) {
      if (w.id >= 15 && w.id <= 18) w = layout.get(w.id - 6);
      else if (w.id == 1 || w.id == 13) return 0; // Room drip and Start are fixed scenery.
      else if (w.id == 14) w = layout.get(7);
      else if (w.id == 20) w = layout.get(5);
      else if (w.id == 65526) w = layout.get(9);
      else if (w.id == 65525 || w.id == 65535) w = layout.get(6);
    }
    if (w == null) return 0;
    return w.x + w.w / 2 < 320 ? -(w.x + w.w) : 640 - w.x;
  }

  float shift(NativeArt.Widget w) {
    double p = Math.min(1, elapsed / (exiting ? EXIT_MS : ENTER_MS));
    double amount = exiting ? 1 - Math.pow(1 - p, 3) : Math.pow(1 - p, 3);
    return (float) (slideDistance(w) * amount);
  }

  boolean campaignChoice(int id) {
    return (name.equals("glucmpgn") || name.equals("gluexpcmpgn")) && id >= 6 && id <= 8;
  }

  boolean labelVisible(int id) {
    NativeArt.Widget w = layout.get(id);
    // Campaign characters stay fixed while the lower controls slide. Their
    // zero slide distance must not reveal either the art or episode labels early.
    if (campaignChoice(id)) return w != null && !exiting && (!slide || elapsed >= ENTER_MS);
    return w != null && (!(exiting || elapsed < ENTER_MS) || slideDistance(w) == 0);
  }

  void draw(Canvas c, double dt) {
    elapsed += dt;
    animationElapsed += dt;
    c.drawBitmap(background.image, 0, 0, NativeArt.PIXELS);
    for (Map.Entry<Integer, Bitmap> entry : fixedPanelDecorations.entrySet()) {
      NativeArt.Widget w = layout.get(entry.getKey());
      c.drawBitmap(entry.getValue(), w.x, w.y, NativeArt.PIXELS);
    }
    for (Layer l : layers) {
      if (campaignChoice(l.widget.id) && !labelVisible(l.widget.id)) continue;
      double layerElapsed = Math.max(0, animationElapsed - (slide && campaignChoice(l.widget.id) ? ENTER_MS : 0));
      boolean active = (hot == l.widget.id && !exiting) || (selectedStart && l.widget.id == 13);
      if (active && !l.wasHot) l.hotTime = 0;
      if (active) l.hotTime += dt;
      l.wasHot = active;
      if ((l.spec.flags & 8) != 0 && !active) continue;
      if (active && name.startsWith("glurdy") && l.spec.name.endsWith("/start.smk")) {
        boolean hasHover = false;
        for (Layer h : layers) if (h.widget.id == l.widget.id && h.spec.name.endsWith("/starton.smk")) hasHover = true;
        if (hasHover) continue;
      }
      double time = (l.spec.flags & 8) != 0 ? l.hotTime : layerElapsed;
      boolean loop = (l.spec.flags & 4) != 0;
      if (l.spec.name.endsWith("/proloop.smk")) {
        time -= projectorIntro(l);
        if (time < 0) continue;
        loop = true;
      } else if (l.spec.name.endsWith("/proj.smk")) {
        boolean hasLoop = false;
        for (Layer h : layers) if (h.widget.id == l.widget.id && h.spec.name.endsWith("/proloop.smk")) hasLoop = true;
        if (hasLoop && time >= (double)l.movie.frames * l.movie.interval) continue;
        loop = false;
      }
      boolean crystal = name.equals("glurdyp") && l.spec.name.endsWith("/glow.smk");
      float brightness = !crystal && (l.spec.flags & 2) != 0 && !active ? .25f : 1;
      if (name.equals("glurdyt") && l.spec.name.endsWith("/start.smk")) brightness = .55f;
      paint.setColorFilter(brightness == 1 ? null : new ColorMatrixColorFilter(
          new float[] {brightness,0,0,0,0, 0,brightness,0,0,0, 0,0,brightness,0,0, 0,0,0,1,0}));
      float alpha = (l.spec.flags & 1) != 0 ? (float)Math.min(1, layerElapsed / 600) : 1;
      // This movie contains the room scenery as well as the glowing crystal.
      // Blend the intact frame with its matching backdrop; SCREEN doubled the
      // scenery and multiplying RGB made a dark rectangle on hover.
      if (crystal && !active) alpha *= .25f;
      paint.setAlpha((int)(255 * alpha));
      paint.setXfermode(null);
      c.save();
      if (name.startsWith("glurdy")) {
        int parent = l.widget.id == 20 ? 5 : l.widget.id == 14 ? 7 : l.widget.id;
        NativeArt.Widget bounds = layout.get(parent);
        if (bounds != null && (parent == 5 || parent == 7))
          c.clipRect(bounds.x + shift(bounds), bounds.y,
              bounds.x + shift(bounds) + bounds.w, bounds.y + bounds.h);
      }
      l.movie.draw(
          c,
          l.widget.x + l.spec.x + shift(l.widget),
          l.widget.y + l.spec.y,
          l.movie.bitmap.getWidth(),
          l.movie.bitmap.getHeight(),
          time,
          loop,
          paint);
      c.restore();
    }
    for (NativeArt.Widget w : layout.widgets) {
      NativeArt.PCX p = panels.get(w.id);
      if (p != null) {
        c.save();
        if (movingPanels.containsKey(w.id)) c.clipRect(w.x + shift(w), w.y,
            w.x + shift(w) + w.w, w.y + w.h);
        if (scaledPanels.contains(w.id))
          c.drawBitmap(p.image, null, new RectF(w.x + shift(w), w.y,
              w.x + shift(w) + w.w, w.y + w.h), NativeArt.PIXELS);
        else c.drawBitmap(movingPanels.containsKey(w.id) ? movingPanels.get(w.id) : p.image,
            w.x + shift(w), w.y, NativeArt.PIXELS);
        c.restore();
      }
    }
  }

  void label(Canvas c, int id, String override, boolean enabled) {
    NativeArt.Widget w = layout.get(id);
    labelCaption(c,id,MenuLabels.forWidget(name,id,w,override),enabled);
  }

  /** Draw a resolved button caption without restoring an intentionally removed mnemonic. */
  void labelCaption(Canvas c, int id, String text, boolean enabled) {
    NativeArt.Widget w = layout.get(id);
    if (!labelVisible(id)) return;
    if (!enabled) text = NativeArt.clean(text);
    NativeArt.Font font =
        (w.flags & 0x4000) != 0 ? button : (w.flags & 0x800) != 0 || w.type == 14 ? large : (w.flags & 0x400) != 0 ? small : tiny;
    int width = Math.max(1, (w.rw > 0 ? w.rw : w.w) - w.tx), height = Math.max(1, (w.rh > 0 ? w.rh : w.h) - w.ty);
    int align = (w.flags & 0x200000) != 0 ? 1 : (w.flags & 0x400000) != 0 ? 2 : 0;
    int layoutWidth = labelWidth(font, text, width, height);
    int textHeight = font.lines(text, layoutWidth).size() * font.height;
    float y = w.y + w.ty + ((w.flags & 0x4000000) != 0 ? height - textHeight
        : (w.flags & 0x2000000) != 0 ? (height - textHeight) / 2f : 0);
    c.save();
    c.translate(w.x + w.tx + shift(w), y);
    c.scale(width / (float)layoutWidth, 1);
    font.draw(c, text, 0, 0, layoutWidth, align,
        enabled ? hot == id ? 4 : w.type == 14 ? 3 : 2 : 5, 0);
    c.restore();
  }

  // Keep the complete translation in fixed classic button artwork. Only narrow
  // text that would otherwise wrap beyond the button; ordinary labels retain
  // the original bitmap font's exact metrics and explicit line breaks.
  static int labelWidth(NativeArt.Font font, String text, int width, int height) {
    if (font.lines(text,width).size()*font.height<=height) return width;
    int widest=width;
    for(String line:PortText.text(text).split("\n"))widest=Math.max(widest,font.measure(line));
    return widest;
  }

  void scrollbar(Canvas c,float x,float y,int height,float value) {
    // Integer rectangles keep the reference's stepped outline crisp at every
    // menu scale. The hollow controls leave the original artwork visible.
    int left=Math.round(x),top=Math.round(y),bottom=top+height;
    scrollbarPaint.setColor(0xffc00024);
    c.drawRect(left+1,top+17,left+2,bottom-16,scrollbarPaint);
    c.drawRect(left+14,top+17,left+15,bottom-16,scrollbarPaint);
    c.drawRect(left+1,top+17,left+15,top+18,scrollbarPaint);
    c.drawRect(left+1,bottom-17,left+15,bottom-16,scrollbarPaint);
    scrollbarPaint.setColor(0xffff0033);
    for(int row=0;row<13;row++) {
      int spread=row/2;
      c.drawRect(left+7-spread,top+1+row,left+9-spread,top+2+row,scrollbarPaint);
      c.drawRect(left+7+spread,top+1+row,left+9+spread,top+2+row,scrollbarPaint);
      c.drawRect(left+7-spread,bottom-2-row,left+9-spread,bottom-1-row,scrollbarPaint);
      c.drawRect(left+7+spread,bottom-2-row,left+9+spread,bottom-1-row,scrollbarPaint);
    }
    c.drawRect(left+1,top+13,left+15,top+14,scrollbarPaint);
    c.drawRect(left+1,bottom-14,left+15,bottom-13,scrollbarPaint);
    // Retain the input handlers' exact center/travel, including their wider
    // invisible grab targets; only the one-pixel red silhouette changes.
    int cy=Math.round(y+23+Math.max(0,Math.min(1,value))*(height-46));
    c.drawRect(left+5,cy-6,left+12,cy-5,scrollbarPaint);
    c.drawRect(left+5,cy+6,left+12,cy+7,scrollbarPaint);
    for(int row=-5;row<=5;row++) {
      int inset=Math.abs(row)==5?2:Math.abs(row)==4?1:0;
      c.drawRect(left+2+inset,cy+row,left+3+inset,cy+row+1,scrollbarPaint);
      c.drawRect(left+14-inset,cy+row,left+15-inset,cy+row+1,scrollbarPaint);
    }
  }

  Bitmap selectFrame(int width, int height) {
    String key = width + ":" + height + ":" + NativeArt.PIXELS.isFilterBitmap();
    if (selectors.containsKey(key)) return selectors.get(key);
    byte[] data = art.read("glue/palnl/dlg.grp");
    Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(result);
    Bitmap left = NativeArt.grp(data, 53, background.palette, true, false),
        middle = NativeArt.grp(data, 54, background.palette, true, false),
        right = NativeArt.grp(data, 55, background.palette, true, false),
        arrow = NativeArt.grp(data, 50, background.palette, true, false);
    if (middle != null) for (int x = 16; x < width - 16; x += 16) c.drawBitmap(middle, x, (height - 16) / 2f, NativeArt.PIXELS);
    if (left != null) c.drawBitmap(left, 0, (height - 16) / 2f, NativeArt.PIXELS);
    if (right != null) c.drawBitmap(right, width - 16, (height - 16) / 2f, NativeArt.PIXELS);
    if (arrow != null) c.drawBitmap(arrow, width - 16, (height - 7) / 2f, NativeArt.PIXELS);
    selectors.put(key, result);
    return result;
  }

  Bitmap panel(int width, int height, boolean button) {
    String key = "panel:" + width + ":" + height + ":" + button + ":" + NativeArt.PIXELS.isFilterBitmap();
    if (selectors.containsKey(key)) return selectors.get(key);
    Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(result);
    byte[] data = art.read("glue/" + paletteName + (button ? "/dlg.grp" : "/tile.grp"));
    Bitmap[] tiles = new Bitmap[button ? 3 : 9];
    for (int i = 0; i < tiles.length; i++) tiles[i] = NativeArt.grp(data, (button ? 103 : 0) + i, background.palette, button, false);
    for (int y = 0; y < height; y += button ? height : 8) for (int x = 0; x < width; x += 8) {
      int col = x == 0 ? 0 : x >= width - 8 ? 2 : 1;
      int row = button ? 0 : y == 0 ? 0 : y >= height - 8 ? 2 : 1;
      Bitmap tile = tiles[row * 3 + col];
      if (tile != null) c.drawBitmap(tile, null, new RectF(x, y, Math.min(width, x + 8), button ? height : Math.min(height, y + 8)), NativeArt.PIXELS);
    }
    selectors.put(key, result);
    return result;
  }

  public void close() {
    for (Layer l : layers) l.movie.close();
    layers.clear();
    for (Bitmap b : movingPanels.values()) b.recycle();
    movingPanels.clear();
    for (Bitmap b : fixedPanelDecorations.values()) b.recycle();
    fixedPanelDecorations.clear();
  }
}
