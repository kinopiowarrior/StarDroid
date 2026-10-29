package org.openbw.androidtest;

import android.graphics.*;
import java.util.*;
import org.json.*;

final class NativeHud implements AutoCloseable {
  static final int WORLD_BOTTOM = 376;
  float resourceScale = 1, commandScale = 1, descriptionScale = 1;
  final NativeArt art;
  final int race;
  final NativeArt.PCX console, tint, portraitOverlay, progressEmpty, progressFull;
  final int[] palette;
  final NativeArt.Font small, tiny, large, errorShadow;
  final byte[] commandGrp, framesGrp, dialogGrp, portraitData;
  final String[] names, portraitNames;
  final JSONObject maps;
  final NativeArt.Dialog production, commandLayout;
  final Map<String, Bitmap> icons = new HashMap<String, Bitmap>();
  final Map<Integer, Bitmap> frames = new HashMap<Integer, Bitmap>();
  final Map<String, Bitmap> panels = new HashMap<String, Bitmap>();
  final Bitmap[] resources = new Bitmap[3], supplyIcons = new Bitmap[3];
  final Bitmap[] dialogButtons = new Bitmap[5];
  int transmissionType = -1;
  boolean transmissionTalk = false;
  final List<Hit> hits = new ArrayList<Hit>();
  int page = 0;
  String previous = "";
  Bitmap wire;
  final Bitmap[] wireTiles = new Bitmap[12], cargoTiles = new Bitmap[8];
  final NativePortrait portrait;
  boolean paused;
  boolean gridHotkeys;
  RectF pressedCommand;
  RectF pressedButton;
  float extra;
  RectF minimap = new RectF();
  Rect miniSource = new Rect();

  static final class Hit {
    RectF rect;
    String[] fields;
    int slot = -1;
    int cancel = -1;
    int selectUnit = -1;
    int unload = -1;
    boolean more = false;
    boolean terrainToggle = false;
    String help;
  }

  NativeHud(NativeArt art, int race, JSONObject maps) {
    this.art = art;
    this.race = race;
    this.maps = maps;
    String letter = new String[] {"z", "t", "p"}[race];
    console = art.pcx("game/" + letter + "console.pcx", true, -1);
    portraitOverlay = art.pcx("game/" + letter + "conover.pcx", true, -1);
    progressEmpty = art.pcx("game/" + letter + "pbrempt.pcx", false, -1);
    progressFull = art.pcx("game/" + letter + "pbrfull.pcx", false, -1);
    commandLayout = new NativeArt.Dialog(art.read("rez/statbtn" + letter + ".bin"));
    tint = art.pcx("unit/cmdbtns/ticon.pcx", false, -1);
    palette = console.palette.clone();
    String era =
        new String[] {
              "badlands", "platform", "install", "ashworld", "jungle", "desert", "ice", "twilight"
            }
            [NativeBridge.value(13, 0) & 7];
    byte[] wpe = art.read("tileset/" + era + ".wpe");
    if (wpe != null && wpe.length >= 1024)
      for (int i = 0; i < 256; i++)
        palette[i] = Color.rgb(wpe[i * 4] & 255, wpe[i * 4 + 1] & 255, wpe[i * 4 + 2] & 255);
    NativeArt.PCX colors = art.pcx("game/tfontgam.pcx", false, -1);
    small = art.font("10", colors, palette, 0);
    errorShadow = small.withShadedColor(0xff000000);
    tiny = art.font("8", colors, palette, 0);
    large = art.font("16", colors, palette, 1);
    commandGrp = art.read("unit/cmdbtns/cmdicons.grp");
    framesGrp = art.read("unit/cmdbtns/" + letter + "cmdbtns.grp");
    dialogGrp = art.read("dlgs/" + new String[] {"zerg", "terran", "protoss"}[race] + ".grp");
    for (int i = 0; i < 5; i++)
      dialogButtons[i] = NativeArt.grp(dialogGrp, i, palette, true, false);
    production = new NativeArt.Dialog(art.read("rez/statdata.bin"));
    names = NativeArt.tbl(art.read("rez/stat_txt.tbl"));
    portraitData = art.read("arr/portdata.dat");
    portraitNames = NativeArt.tbl(art.read("arr/portdata.tbl"));
    portrait = new NativePortrait(art, portraitData, portraitNames);
    byte[] grp = art.read("game/icons.grp");
    int[] ids = {0, 1 + race, 4 + race};
    for (int i = 0; i < 3; i++) {
      resources[i] = NativeArt.grp(grp, ids[i], palette, true, false);
      supplyIcons[i] = NativeArt.grp(grp, 4 + i, palette, true, false);
    }
    // The original conover image owns the entire portrait rim. Clearing just
    // one palette index leaves stale console pixels over the moving portrait.
    int[] px = new int[60 * 56];
    console.image.getPixels(px, 0, 60, 413, 410, 60, 56);
    for (int y = 0; y < 56; y++)
      for (int x = 0; x < 60; x++)
        px[y * 60 + x] = 0;
    Bitmap mutable = console.image.copy(Bitmap.Config.ARGB_8888, true);
    mutable.setPixels(px, 0, 60, 413, 410, 60, 56);
    console.image = mutable;
  }

  static int number(String[] a, int i, int fallback) {
    try {
      return Integer.parseInt(a[i]);
    } catch (Exception e) {
      return fallback;
    }
  }

  Bitmap frame(int id) {
    if (!frames.containsKey(id))
      frames.put(id, NativeArt.grp(framesGrp, id, palette, false, false));
    return frames.get(id);
  }

  Bitmap icon(int id, int ramp) {
    if (id < 0) return null;
    String key = id + ":" + ramp;
    if (icons.containsKey(key)) return icons.get(key);
    int[] colors = tint.palette.clone();
    for (int i = 0; i < 16; i++) colors[i] = tint.palette[tint.pixels[ramp * 16 + i] & 255];
    Bitmap b = NativeArt.grp(commandGrp, id, colors, false, true);
    icons.put(key, b);
    return b;
  }

  void image(Canvas c, Bitmap b, float x, float y) {
    if (b != null) c.drawBitmap(b, x, y, NativeArt.PIXELS);
  }

  Bitmap panel(int width, int height, boolean button, boolean enabled) {
    String key = width + ":" + height + ":" + button + ":" + enabled + ":" + NativeArt.PIXELS.isFilterBitmap();
    Bitmap old = panels.get(key);
    if (old != null) return old;
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    if (button) {
      int start = enabled ? 115 : 112;
      Bitmap[] tiles = new Bitmap[3];
      for (int i = 0; i < 3; i++)
        tiles[i] = NativeArt.grp(dialogGrp, start + i, palette, true, false);
      for (int x = 0; x < width; x += 8) {
        Bitmap tile = tiles[x == 0 ? 0 : x >= width - 8 ? 2 : 1];
        if (tile != null)
          canvas.drawBitmap(
              tile, null, new RectF(x, 0, Math.min(width, x + 8), height), NativeArt.PIXELS);
      }
    } else {
      Paint glass = new Paint();
      glass.setColor(0xb01a2c72);
      canvas.drawRoundRect(new RectF(0, 0, width, height), 2, 2, glass);
      glass.setStyle(Paint.Style.STROKE);
      glass.setColor(0xb03d42b1);
      canvas.drawRoundRect(new RectF(.5f, .5f, width - .5f, height - .5f), 2, 2, glass);
    }
    panels.put(key, bitmap);
    return bitmap;
  }

  void draw(Canvas c, Bitmap world, int width, float zoom, double now) {
    extra = width - 640;
    hits.clear();
    int type = NativeBridge.value(1, 0);
    String wiresText = NativeBridge.text(3);
    int identity = wiresText.isEmpty() ? -1 : number(wiresText.split("\t"), 0, -1);
    int readyType = NativeBridge.value(30, 0);
    int shownType = transmissionType >= 0 ? transmissionType : readyType >= 0 ? readyType : type;
    portrait.selection(transmissionType >= 0 ? -2 - transmissionType : readyType >= 0 ? -232 - readyType : identity);
    boolean talk = NativeBridge.value(12, 0) != 0;
    portrait.target(shownType, transmissionType >= 0 ? transmissionTalk : readyType >= 0 || talk);
    // Empty portraits must be black too: the console contains a transparent
    // video opening, so otherwise live terrain leaks through its corner pixels.
    Paint backdrop = new Paint(); backdrop.setColor(Color.rgb(8,8,8));
    c.drawRect(413 + extra,410,473 + extra,466,backdrop);
    portrait.draw(c, 413 + extra, 410, 60, 56, now, paused, false);
    if (extra == 0) c.drawBitmap(console.image, 0, 0, NativeArt.PIXELS);
    else {
      c.drawBitmap(
          console.image, new Rect(0, 300, 216, 480), new RectF(0, 300, 216, 480), NativeArt.PIXELS);
      // Match the mirrored extensions in the previous interface, without stretching
      // the console across the whole added width.
      for (float offset = 0; offset < extra; ) {
        float pair = Math.min(128, extra - offset), half = pair / 2;
        c.drawBitmap(console.image, new Rect(216, 300, 280, 480),
            new RectF(216 + offset, 300, 216 + offset + half, 480), NativeArt.PIXELS);
        c.save();
        c.translate(216 + offset + pair, 0);
        c.scale(-1, 1);
        c.drawBitmap(console.image, new Rect(216, 300, 280, 480),
            new RectF(0, 300, half, 480), NativeArt.PIXELS);
        c.restore();
        offset += pair;
      }
      c.drawBitmap(
          console.image,
          new Rect(216, 300, 640, 480),
          new RectF(216 + extra, 300, 640 + extra, 480),
          NativeArt.PIXELS);
    }
    image(c, portraitOverlay.image, 413 + extra, 410);
    int mw = NativeBridge.value(9, 0),
        mh = NativeBridge.value(10, 0),
        sw = Math.round(mw * 128f / Math.max(mw, mh) / zoom),
        sh = Math.round(mh * 128f / Math.max(mw, mh) / zoom);
    miniSource.set(4, world.getHeight() - 4 - sh, 4 + sw, world.getHeight() - 4);
    float fit = 128f / Math.max(mw, mh);
    minimap.set(
        6 + (128 - mw * fit) / 2,
        347 + (128 - mh * fit) / 2,
        6 + (128 + mw * fit) / 2,
        347 + (128 + mh * fit) / 2);
    if (miniSource.top >= 0) c.drawBitmap(world, miniSource, minimap, NativeArt.PIXELS);
    boolean menuDown = pressedButton != null && pressedButton.contains(442 + extra, 397);
    Bitmap menu = dialogButtons[menuDown ? 1 : 0];
    image(c, menu, 410 + extra, 387);
    small.draw(c, "MENU", 410 + extra, menuDown ? 392 : 391, 65, 1, menuDown ? 2 : 3, 0);
    Bitmap terrain = dialogButtons[NativeBridge.value(14, 0) != 0 ? 4 : 3];
    boolean terrainDown = pressedCommand != null && pressedCommand.contains(36, 333);
    image(c, terrain, 6, terrainDown ? 324 : 323);
    hits.add(terrainHit(NativeBridge.value(14, 0) != 0));
    drawResources(c, width);
    String card = NativeBridge.text(0), details = NativeBridge.text(4);
    String[] lines = card.split("\n"),
        title = lines[0].split("\t", -1),
        wires = wiresText.split("\n");
    int count = wiresText.isEmpty() ? 0 : wires.length;
    float info = 138, wireX = 168 + extra * .4f, nameX = 240 + extra * .6f;
    if (count > 0) {
      int box = NativeBridge.value(6, 0);
      for (int i = 0; i < count; i++) {
        if (wireTiles[i] == null || wireTiles[i].getWidth() != box)
          wireTiles[i] = Bitmap.createBitmap(box, box, Bitmap.Config.ARGB_8888);
        wire = wireTiles[i];
        NativeBridge.wire(wire, i);
        int columns = (count + 1) / 2;
        float gap = Math.min(18, 3 + extra * .04f);
        float groupWidth = columns * 33 + (columns - 1) * gap;
        float start = extra > 0 ? 168 + (213 + extra - groupWidth) / 2 : 168;
        float x = count == 1 ? wireX : start + (i % columns) * (33 + gap),
            y = count == 1 ? 388 : 396 + (i / ((count + 1) / 2)) * 37;
        if (count > 1) image(c, frame(number(wires[i].split("\t"), 6, 13)), x, y);
        image(c, wire, x, y);
        Hit hit = new Hit();
        hit.rect = new RectF(x, y, x + box, y + box);
        hit.selectUnit = number(wires[i].split("\t"), 0, -1);
        hits.add(hit);
      }
      if (count == 1) {
        small.draw(
            c, type >= 0 && type < names.length ? names[type] : title[0], nameX, 388, 152, 1, 2, 0);
        String[] values = details.split("\n", 2)[0].split("\t");
        String life = title.length > 1 ? title[1].replace("HP ", "") : "";
        int color = healthColor(life);
        life = Character.toString((char) color) + life;
        if (!details.isEmpty() && number(values, 1, -1) >= 0)
          life = "\u0002" + values[1] + "/" + values[2] + " " + life;
        // Center the combined shield/HP readout without wrapping it at 80px.
        tiny.draw(c, life, 200 + extra * .4f, 452, 0, 1, color, 0);
        String second = title.length > 2 ? title[2] : "";
        boolean resourceAmount = second.startsWith("Gas") || second.startsWith("Minerals");
        if (!details.isEmpty() && !resourceAmount)
          second = number(values, 3, -1) >= 0 ? "\u0010" + values[3] + "/" + values[4] : "";
        if (resourceAmount) {
          second = second.replaceFirst("^Gas\\s*:?\\s*", "Vespene Gas: ")
              .replaceFirst("^Minerals\\s*:?\\s*", "Minerals: ");
          small.draw(c, second, nameX, 434, 152, 1, 2, 0);
        } else tiny.draw(c, second, 160 + extra * .4f, 464, 80, 1, 2, 0);
        drawUnitStatus(c, title.length > 3 ? title[3] : "",
            !details.isEmpty() && number(values, 6, 0) != 0, nameX);
      }
    }
    String status = NativeBridge.text(2);
    if (count == 1 && !status.isEmpty()) drawProduction(c, status, info + extra * .6f, 388);
    else if (count == 1 && !NativeBridge.text(14).isEmpty()) drawCargo(c, NativeBridge.text(14), extra);
    else if (count == 1) drawDetails(c, details, extra);
    drawCommands(c, lines, type, title[0], width);
  }

  void drawUnitStatus(Canvas c, String status, boolean detector, float x) {
    String label = status;
    if (detector) label += (label.isEmpty() ? "" : "\u0002 · ") + "\u0003" + string(830, "Detector");
    if (label.isEmpty()) return;
    // Effects and the detector label share one bounded row, above kills/supply.
    // Long effect lists stay readable through the existing inspection tooltip.
    NativeArt.Font font = small.measure(label) <= 152 ? small : tiny;
    String shown = label;
    if (font.measure(shown) > 152) {
      while (!shown.isEmpty() && font.measure(shown + "...") > 152)
        shown = shown.substring(0, shown.length() - 1);
      shown += "...";
    }
    c.save(); c.clipRect(x, 402, x + 152, 417);
    font.draw(c, shown, x, 403, 152, 1, 2, 0);
    c.restore();
    Hit hit = new Hit(); hit.rect = new RectF(x, 402, x + 152, 417);
    hit.help = NativeArt.clean(status) + (detector ? (status.isEmpty() ? "" : "\n") + NativeArt.clean(string(830, "Detector")) : "");
    hits.add(hit);
  }

  void drawCargo(Canvas c, String cargo, float extra) {
    String[] rows = cargo.trim().split("\n");
    int capacity = number(rows[0].split("\t"), 6, 8);
    // statdata.bin owns the original cargo positions. Place 4-space units first,
    // then tall 2-space units, then single cells, in original transport order.
    int four = capacity < 7 ? 20 : 18, two = capacity < 7 ? 22 : 21, one = capacity < 7 ? 27 : 25;
    for (int space : new int[] {4, 2, 1}) for (int i = 0; i < Math.min(8, rows.length); i++) {
      String[] data = rows[i].split("\t");
      if (number(data, 4, 1) != space) continue;
      NativeArt.Widget w = production.get(space == 4 ? four : space == 2 ? two : one);
      four += space / 4; two += space / 2; one += space;
      if (w == null) continue;
      float x = 138 + extra * .6f + w.x, y = 388 + w.y;
      if (cargoTiles[i] == null) cargoTiles[i] = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
      NativeBridge.cargoWire(cargoTiles[i], i);
      image(c, frame(number(data, 5, 13)), x, y);
      int width = space == 4 ? 64 : 32, height = space == 1 ? 32 : 64;
      float dx = x + (w.w - width) / 2f, dy = y + (w.h - height) / 2f;
      c.drawBitmap(cargoTiles[i], new Rect(0, 0, width, height),
          new RectF(dx, dy, dx + width, dy + height), NativeArt.PIXELS);
      Hit hit = new Hit(); hit.rect = new RectF(x, y, x + w.w, y + w.h);
      hit.unload = number(data, 0, -1);
      hit.help = NativeArt.clean(string(number(data, 1, -1), "Passenger")) + " — " + space
          + (space == 1 ? " cargo space" : " cargo spaces") + "\nTap to unload.";
      hits.add(hit);
    }
  }

  float commandErrorTop(String message, int width) {
    int textWidth = Math.min(460, Math.round((width - 80) / descriptionScale));
    int lines = small.lines(message, textWidth).size();
    float bottom = Math.min(WORLD_BOTTOM - 12, 480 - 126 * commandScale - 8);
    return bottom - lines * 14 * descriptionScale;
  }

  void commandError(Canvas c, String message, int width) {
    // Ordinary in-game text, centered above the console, with no tooltip panel.
    int textWidth = Math.min(460, Math.round((width - 80) / descriptionScale));
    c.save();
    c.translate((width - textWidth * descriptionScale) / 2f, commandErrorTop(message, width));
    c.scale(descriptionScale, descriptionScale);
    errorShadow.draw(c, message, 1, 1, textWidth, 1, 2, 14);
    small.draw(c, message, 0, 0, textWidth, 1, 2, 14);
    c.restore();
  }

  void drawResources(Canvas c, int width) {
    c.save();
    c.translate(width - 204 * resourceScale, 2);
    c.scale(resourceScale, resourceScale);
    String[] resource = NativeBridge.text(1).split("\t");
    for (int i = 0; i < 2; i++) {
      image(c, resources[i], i * 68, 0);
      small.draw(c, resource.length > i ? resource[i] : "", 16 + i * 68, 0, 50, 0, 7, 0);
    }
    int supplyMask = number(resource, 4, 1 << race), row = 0;
    int[] order = {race, (race + 1) % 3, (race + 2) % 3};
    for (int sr : order) if ((supplyMask & (1 << sr)) != 0) {
      int used = number(resource, 5 + sr * 2, number(resource, 2, 0));
      int available = number(resource, 6 + sr * 2, number(resource, 3, 0));
      float y = row++ * 18;
      image(c, supplyIcons[sr], 136, y);
      small.draw(c, used + "/" + available, 152, y, 50, 0, used > available ? 6 : 7, 0);
    }
    c.restore();
  }

  void commandModeButton(Canvas c, RectF rect, boolean attack) {
    Paint ink = new Paint();ink.setColor(0xe0000000);c.drawRect(rect, ink);
    c.drawBitmap(frame(0), null, rect, NativeArt.PIXELS);
    Bitmap glyph = icon(attack ? 230 : 228, 0);
    if (glyph != null) c.drawBitmap(glyph, null,
        new RectF(rect.left + 4, rect.top + 4, rect.right - 4, rect.bottom - 4), NativeArt.PIXELS);
  }

  void selectionButton(Canvas c, RectF rect, boolean armed) {
    Paint ink = new Paint();ink.setColor(0xe0000000);c.drawRect(rect, ink);
    c.drawBitmap(frame(0), null, rect, NativeArt.PIXELS);
    c.save();c.translate(rect.left + 9, rect.top + 7);
    Path cursor = new Path();cursor.moveTo(0,0);cursor.lineTo(0,23);cursor.lineTo(6,17);
    cursor.lineTo(12,28);cursor.lineTo(17,25);cursor.lineTo(11,15);cursor.lineTo(21,15);cursor.close();
    ink.setColor(armed ? 0xfff8e67a : 0xff52d878);c.drawPath(cursor,ink);
    ink.setStyle(Paint.Style.STROKE);ink.setColor(0xff163b25);ink.setStrokeWidth(1);c.drawPath(cursor,ink);c.restore();
    if (armed) {ink.setColor(0xfff8e67a);c.drawRect(rect.left+1,rect.top+1,rect.right-1,rect.bottom-1,ink);}
  }

  static int healthColor(String life) {
    try {
      String[] hp = life.split("/");
      int percent = Integer.parseInt(hp[0]) * 100 / Math.max(1, Integer.parseInt(hp[1]));
      return percent > 66 ? 7 : percent >= 33 ? 3 : 6;
    } catch (Exception ignored) {
      return 7;
    }
  }

  String string(int index, String fallback) {
    return index >= 0 && index < names.length && !names[index].isEmpty() ? names[index] : fallback;
  }

  String requirement(String[] command) {
    if (command.length > 7 && !command[7].isEmpty()) return command[7];
    // Some Zerg morph cards omit their requirement label. This is display
    // metadata only; availability and orders remain controlled by the engine.
    JSONObject units = maps.optJSONObject("requirementUnits");
    int id = units == null || command.length < 4 ? -1 : units.optInt(command[3], -1);
    if (id >= 0) return NativeArt.clean(string(id, "Technology"));
    JSONObject tech = maps.optJSONObject("requirementTech");
    return tech == null || command.length < 4 ? "" : tech.optString(command[3], "");
  }

  void drawDetails(Canvas c, String raw, float extra) {
    if (raw.isEmpty()) return;
    String[] lines = raw.split("\n"), data = lines[0].split("\t");
    boolean building = number(data, 5, 0) != 0,
        supply = number(data, 7, 0) > 0 && number(data, 11, 0) == 0;
    float x = 242 + extra * .6f;
    if (supply) {
      int supplyRace = number(data, 10, race);
      int[] ids = {820 + supplyRace, 814 + supplyRace, 817 + supplyRace, 823 + supplyRace};
      String[] labels = {"Used:", "Provided:", "Total:", "Max:"};
      int[] values = {number(data, 8, 0), number(data, 7, 0), number(data, 9, 0), 200};
      for (int i = 0; i < 4; i++)
        tiny.draw(c, string(ids[i], labels[i]) + " " + values[i], 281 + extra * .6f, 418 + i * 14, 111, 1, 2, 0);
    }
    if (!building && !supply && (raw.contains("\nweapon\t") || raw.contains("\nammo\t")))
      small.draw(c, "Kills: " + data[0], 240 + extra * .6f, 418, 152, 1, 2, 0);
    if (building && !raw.contains("\nweapon\t")) return;
    ArrayList<String[]> rows = new ArrayList<String[]>();
    for (int i = 1; i < lines.length; i++) {
      String[] a = lines[i].split("\t");
      if (building && a[0].equals("armor")) continue;
      rows.add(a);
    }
    Collections.sort(rows, (a, b) -> order(a[0]) - order(b[0]));
    int i = 0;
    for (String[] a : rows) {
      if (supply && !a[0].equals("armor")) continue;
      if (i == 4) break;
      image(c, icon(number(a, 1, -1), 5), x + i * 39, 440);
      image(c, frame(12), x + i * 39, 439);
      tiny.draw(c, a[2], x + i * 39 + 22, 466, 12, 2, 2, 0);
      Hit hit = new Hit();
      hit.rect = new RectF(x + i * 39, 439, x + i * 39 + 36, 475);
      String label = a[0].equals("ammo") ? number(a, 1, 0) == 85 ? "Scarabs" : "Interceptors"
          : string(number(a, 6, 0) - 1, a[0].equals("weapon") ? "Damage" : "Armor");
      String amount = a[3] + (number(a, 4, 0) != 0 ? "+" + a[4] : "")
          + (number(a, 5, 1) > 1 ? " (×" + a[5] + ")" : "");
      hit.help = label + (a[0].equals("ammo") ? ": " + a[2] : "") + "\n"
          + (a[0].equals("weapon") || a[0].equals("ammo") ? "Damage: " : "Armor: ") + amount
          + (a[0].equals("ammo") ? "" : "\nUpgrade level: " + a[2]);
      hits.add(hit);
      i++;
    }
  }

  static int order(String s) {
    return s.equals("armor") ? 0 : s.equals("shield") ? 1 : s.equals("weapon") ? 2 : 3;
  }

  void drawProduction(Canvas c, String text, float x, float y) {
    String[] fields = text.split("\t");
    int kind = NativeBridge.value(3, 0), target = NativeBridge.value(4, 0);
    boolean merging = kind == 5, morph = kind == 4 || merging;
    int label = morph && target < 0 ? 65506 : 65521,
        bar = morph && target < 0 ? 13 : kind == 2 ? 14 : kind == 3 ? 16 : 7;
    NativeArt.Widget l = production.get(label), b = production.get(bar);
    int type = NativeBridge.value(1, 0), unitRace = NativeBridge.value(5, 0);
    if (unitRace < 0 || unitRace > 2) unitRace = race;
    int labelIndex = morph ? 798 + unitRace : kind == 2 || kind == 3 ? -1
        : (type == 160 || type == 167 ? 770 : 773) + unitRace;
    String title = merging ? "Summoning" : (type == 36 || type == 59 || type == 97) ? "Morphing"
        : string(labelIndex, kind == 2 ? "Researching" : kind == 3 ? "Upgrading" : "Building");
    if (l != null) small.draw(c, title, x + l.x, y + l.y, l.w, 1, 2, 0);
    if (b != null) {
      image(c, progressEmpty.image, x + b.x, y + b.y);
      float w = 2 + (progressFull.width - 4) * Math.max(0, Math.min(100, number(fields, 0, 0))) / 100f;
      c.save(); c.clipRect(x + b.x, y + b.y, x + b.x + w, y + b.y + b.h);
      image(c, progressFull.image, x + b.x, y + b.y); c.restore();
    }
    int n = morph ? (target >= 0 ? 1 : 0) : kind == 1 ? 5 : 1;
    for (int i = 0; i < n; i++) {
      NativeArt.Widget w = production.get(i + 2);
      if (w == null) continue;
      image(c, frame(i == 0 || i == 4 ? 2 : 4), x + w.x, y + w.y);
      int id = i == 0 ? NativeBridge.value(2, 0) : NativeBridge.value(4, i);
      if (id >= 0) {
        image(c, icon(id, 0), x + w.x, y + w.y + 1);
        if (kind == 1) tiny.draw(c, Integer.toString(i + 1), x + w.x + 2, y + w.y + 2, 0, 0, i == 0 ? 3 : 2, 0);
        Hit hit = new Hit();
        hit.rect = new RectF(x + w.x, y + w.y, x + w.x + w.w, y + w.y + w.h);
        hit.cancel = i;
        hits.add(hit);
      } else if (kind == 1) {
        Bitmap number = frame(i + 6);
        if (number != null) image(c, number, x + w.x + 18 - number.getWidth() / 2f,
            y + w.y + 18 - number.getHeight() / 2f);
      }
    }
    if (kind == 1) {
      NativeArt.Widget connector = production.get(8);
      if (connector != null) {
        c.save();
        c.clipRect(x + connector.x, y + connector.y, x + connector.x + connector.w, y + connector.y + connector.h);
        image(c, frame(11), x + connector.x, y + connector.y);
        c.restore();
      }
    }
  }

  /** One layout model for rendering and fresh keyboard dispatch, including submenus and pages. */
  private List<Hit> commandBindings(String[] lines, int type, String title) {
    String signature = String.join("\n", Arrays.copyOfRange(lines, 1, lines.length));
    if (!previous.equals(signature)) {
      page = 0;
      previous = signature;
    }
    List<String[]> commands = new ArrayList<String[]>();
    boolean worker = type == 7 || type == 41 || type == 64;
    for (int i = 1; i < lines.length; i++) {
      String[] f = lines[i].split("\t", -1);
      if (f.length < 3) continue;
      if (worker && (f[1].equals("Patrol") || f[1].startsWith("Hold"))) continue;
      commands.add(f);
    }
    boolean build = title.equals("Build") || title.equals("Advanced Build");
    if ((build || NativeBridge.value(0, 0) == 2)
        && commands.stream().noneMatch(a -> a[0].equals("\u001b")))
      commands.add(new String[] {"\u001b", "Cancel", "1", "-1"});
    int pages = commands.size() > 9 ? (commands.size() + 7) / 8 : 1;
    boolean[] used = new boolean[9];
    if (pages > 1) {
      int from = (page % pages) * 8;
      commands = commands.subList(from, Math.min(from + 8, commands.size()));
      used[8] = true;
    }
    int[] positions = new int[commands.size()];
    Arrays.fill(positions, -1);
    for (int i = 0; i < commands.size(); i++) {
      String[] f = commands.get(i);
      int slot = -1;
      if (pages == 1) {
        if (f[0].equals("\u001b")) slot = 8;
        else if (build) slot = map("buildSlots", Integer.toString(number(f, 3, -1)));
        else if (number(f, 11, -1) >= 0) slot = map("upgradeSlots", f[11]);
        else if (number(f, 12, -1) >= 0) slot = map("researchSlots", f[12]);
        else {
          slot = map("slots", f[1]);
          if (f[1].equals("Set Rally Point") && (type == 130 || type == 131 || type == 132 || type == 133)) slot = 1;
          if (slot < 0) {
            JSONObject t =
                maps.optJSONObject("trainingSlots").optJSONObject(Integer.toString(type));
            if (t != null) slot = t.optInt(Integer.toString(number(f, 3, -1)), -1);
          }
          slot = CommandSlots.override(type, f[1], slot);
        }
      }
      if (slot >= 0 && slot < 9 && !used[slot]) {
        positions[i] = slot;
        used[slot] = true;
      }
    }
    for (int i = 0; i < positions.length; i++)
      if (positions[i] < 0)
        for (int j = 0; j < 9; j++)
          if (!used[j]) {
            positions[i] = j;
            used[j] = true;
            break;
          }
    List<Hit> bindings = new ArrayList<Hit>();
    for (int i = 0; i < commands.size(); i++) {
      if (positions[i] < 0) continue;
      Hit hit = new Hit(); hit.slot = positions[i]; hit.fields = commands.get(i); bindings.add(hit);
    }
    if (pages > 1) { Hit more = new Hit(); more.slot = 8; more.more = true; bindings.add(more); }
    return bindings;
  }

  Hit commandHitForSlot(int slot, String card, int type) {
    if (slot < 0 || slot >= 9 || card == null || card.isEmpty()) return null;
    String[] lines = card.split("\n");
    String title = lines[0].split("\t", 2)[0];
    for (Hit hit : commandBindings(lines, type, title)) if (hit.slot == slot) return hit;
    return null;
  }

  String hotkey(Hit hit) {
    if (hit.slot < 0 || hit.slot >= 9) return "";
    if (gridHotkeys) return Character.toString("QWEASDZXC".charAt(hit.slot));
    return CommandHint.originalHotkey(hit.fields);
  }

  void drawCommands(Canvas c, String[] lines, int type, String title, int width) {
    c.save(); c.translate(width, 480); c.scale(commandScale, commandScale); c.translate(-width, -480);
    int firstCommandHit = hits.size();
    for (Hit hit : commandBindings(lines, type, title)) {
      RectF button = commandRect(hit.slot, width);
      if (hit.more) {
        image(c, frame(0), button.left, button.top);
        tiny.draw(c, "More", button.left, button.top + 10, 36, 1, 3, 0);
        hit.rect = new RectF(button.left, button.top, button.left + 40, button.top + 38);
        hits.add(hit); continue;
      }
      String[] f = hit.fields;
      float x = button.left, y = button.top;
      image(c, frame(0), x, y);
      int id = number(f, 10, number(f, 3, -1));
      if (id < 0) id = map("orderIcons", f[1]);
      int builderRace = NativeBridge.value(5, 0); if (builderRace < 0 || builderRace > 2) builderRace = race;
      if (f[1].equals("Build")) id = new int[] {257, 234, 272}[builderRace];
      if (f[1].equals("Advanced")) id = new int[] {258, 235, 273}[builderRace];
      Bitmap icon = icon(id, f[2].equals("0") ? 1 : number(f, 9, 0) == 1 ? 2 : 0);
      if (icon != null) image(c, icon, x, y);
      else tiny.draw(c, f[1], x + 2, y + 8, 34, 1, 3, 0);
      hit.rect = new RectF(x, y, x + 36, y + 34);
      if (pressedCommand != null && pressedCommand.contains(width + (x + 18 - width) * commandScale,
          480 + (y + 18 - 480) * commandScale)) {
        Paint highlight = new Paint();
        highlight.setStyle(Paint.Style.STROKE);
        highlight.setStrokeWidth(1);
        highlight.setColor(f[2].equals("0") ? 0xff747b8a : 0xffeee5a2);
        c.drawRect(x + 1, y + 1, x + 35, y + 35, highlight);
      }
      hits.add(hit);
    }
    c.restore();
    for (int i = firstCommandHit; i < hits.size(); i++) {
      RectF r = hits.get(i).rect;
      r.set(width + (r.left - width) * commandScale, 480 + (r.top - 480) * commandScale,
          width + (r.right - width) * commandScale, 480 + (r.bottom - 480) * commandScale);
    }
  }

  String hint(Hit hit) {
    if (hit.unload >= 0) return hit.help == null ? "Unload passenger" : hit.help;
    if (hit.fields != null) return CommandHint.describe(hit.fields, requirement(hit.fields), hotkey(hit));
    if (hit.help != null) return hit.help;
    if (hit.cancel >= 0) return "Cancel production\nCancels this item in the production queue.";
    if (hit.more) return "More commands" + (hotkey(hit).isEmpty() ? "" : " [\u0003" + hotkey(hit) + "\u0002]") + "\nShows the next page of commands.";
    if (hit.selectUnit >= 0) return "Select unit\nSelects this unit from the group.";
    return "";
  }

  static Hit terrainHit(boolean shown) {
    Hit hit = new Hit();
    hit.rect = new RectF(6, 322, 67, 344);
    hit.terrainToggle = true;
    hit.help = PortText.text(shown ? "Hide minimap terrain" : "Show minimap terrain")
        + " [\u0003Tab\u0002]\n"
        + PortText.text(shown ? "Hides terrain on the minimap. Unit markers remain visible."
            : "Shows terrain on the minimap.");
    return hit;
  }

  void tooltip(Canvas canvas, String text, int width) {
    tooltip(canvas, text, width, race);
  }

  void tooltip(Canvas canvas, String text, int width, int supplyRace) {
    tooltip(canvas, text, width, supplyRace, Float.POSITIVE_INFINITY);
  }

  void tooltip(Canvas canvas, String text, int width, int supplyRace, float bottomLimit) {
    int boxWidth=260,lineHeight=Math.max(15,small.height+3);
    List<String> rows=new ArrayList<String>();
    for(String row:text.replace("\r\n","\n").replace('\r','\n').split("\n",-1)) {
      // Translate the title separately so localization cannot erase the key color.
      int keyAt = row.lastIndexOf(" [\u0003");
      if (keyAt >= 0 && row.endsWith("\u0002]")) row = PortText.text(row.substring(0, keyAt)) + row.substring(keyAt);
      if(row.matches("[0-9].* (minerals|Vespene Gas|supply|energy).*")) {
        String current="";int used=0;
        for(String item:row.split(" · ")) {
          boolean resource=item.endsWith("minerals")||item.endsWith("Vespene Gas")||item.endsWith("supply");
          String label=resource?item.substring(0,item.indexOf(' ')):item;int n=small.measure(label)+(resource?17:0)+14;
          if(used>0&&used+n>boxWidth-16){rows.add("\u0000"+current);current="";used=0;}
          current+=(current.isEmpty()?"":" · ")+item;used+=n;
        }
        rows.add("\u0000"+current);
      }else rows.addAll(small.lines(row,boxWidth-16));
    }
    int height=rows.size()*lineHeight+14;
    float bottom=Math.min(Math.min(346,480-126*commandScale-8),Math.max(16,bottomLimit));
    float scale=Math.min(descriptionScale,Math.min((width-16f)/boxWidth,(bottom-8f)/height));
    float left=width-boxWidth*scale-8,top=bottom-height*scale;
    Paint ink=new Paint();canvas.save();canvas.translate(left,top);canvas.scale(scale,scale);
    ink.setColor(0xf5081010);canvas.drawRect(0,0,boxWidth,height,ink);
    ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1);
    ink.setColor(race == 0 ? 0xffe43c3c : race == 2 ? 0xfff0cc48 : 0xff4088ff);
    canvas.drawRect(.5f,.5f,boxWidth-.5f,height-.5f,ink);
    ink.setColor(race == 0 ? 0xff581818 : race == 2 ? 0xff584814 : 0xff183458);
    canvas.drawRect(1.5f,1.5f,boxWidth-1.5f,height-1.5f,ink);canvas.clipRect(4,4,boxWidth-4,height-4);
    float y=7;
    for(String row:rows){
      if(row.startsWith("\u0000")){float x=8;for(String item:row.substring(1).split(" · ")){
        int resource=item.endsWith("minerals")?0:item.endsWith("Vespene Gas")?1:item.endsWith("supply")?2:-1;
        String label=resource>=0?item.substring(0,item.indexOf(' ')):item;
        if(resource>=0){Bitmap glyph=resource==2?supplyIcons[Math.max(0,Math.min(2,supplyRace))]:resources[resource];image(canvas,glyph,x,y-2);x+=17;}
        small.draw(canvas,label,x,y,0,0,2,0);x+=small.measure(label)+14;
      }}else small.draw(canvas,row,8,y,0,0,2,0);
      y+=lineHeight;
    }
    canvas.restore();
  }

  RectF commandRect(int slot, int width) {
    NativeArt.Widget w = commandLayout.get(slot + 1);
    return new RectF(width - 144 + w.x, 354 + w.y,
        width - 144 + w.x + w.w, 354 + w.y + w.h);
  }

  int map(String key, String value) {
    JSONObject o = maps.optJSONObject(key);
    return o == null ? -1 : o.optInt(value, -1);
  }

  Hit hit(float x, float y) {
    for (Hit h : hits) if (h.rect.contains(x, y)) return h;
    return null;
  }

  public void close() {
    portrait.close();
    for (Bitmap tile : cargoTiles) if (tile != null) tile.recycle();
    for (Bitmap tile : wireTiles) if (tile != null) tile.recycle();
  }
}
