#!/usr/bin/env python3
"""Check shared bitmap sampling and cache changes with production render code.

Run run_menu_recovery.py DATA first to prepare its AWT shims and legal fixtures.
This is host rendering, not Android GPU or device verification.
"""
from pathlib import Path
import hashlib, json, os, re, subprocess

root = Path(__file__).resolve().parents[1]
src = root/'app/src/main/java/org/openbw/androidtest'
menu = root/'build-native/menu76'
out = root/'build-native/graphics87'
out.mkdir(parents=True, exist_ok=True)
assert (menu/'classes').is_dir(), 'Run tests/run_menu_recovery.py DATA first'

# Audit every bitmap draw, including the battlefield, HUD, thumbnails, story,
# cursors, movies, fonts and masked briefing portraits. New ungoverned paints
# must be reviewed instead of silently escaping the global setting.
calls = []
for path in sorted(src.glob('*.java')):
    text = path.read_text(encoding='utf-8')
    for match in re.finditer(r'\.drawBitmap\(', text):
        at = match.end(); depth = 1
        while depth:
            if text[at] == '(' : depth += 1
            if text[at] == ')' : depth -= 1
            at += 1
        call = re.sub(r'\s+', ' ', text[match.end():at-1].strip())
        assert any(call.endswith(', '+p) or call.endswith(','+p) for p in
                   ['PIXELS', 'NativeArt.PIXELS', 'bitmapPaint(paint)',
                    'NativeArt.bitmapPaint(glue.maskPaint)']), (path.name, call)
        calls.append(path.name+':'+str(text.count('\n', 0, match.start())+1))
surface = (src/'NativeSurface.java').read_text(encoding='utf-8')
assert 'worldPaint' not in surface
assert surface.count('toggle(c, "Smooth graphics (game + UI)"') == 1
assert surface.count('.putBoolean("smooth-graphics"') == 1
loop = surface[surface.index('  private void loop()'):]
assert loop.index('loadGraphicsSettings();') < loop.index('loadArchives();')
print('PASS shared sampling audit:', len(calls), 'bitmap drawing sites; one toggle and preference')

sources = {p.relative_to(menu).as_posix(): p.read_text(encoding='utf-8')
           for p in (menu/'android').rglob('*.java')}
paint = sources['android/graphics/Paint.java']
paint = paint.replace('public class Paint{', 'public class Paint{public enum Style{STROKE,FILL} public void setStyle(Style s){} public Object colorFilter,xfer;')
paint = paint.replace('setColorFilter(ColorMatrixColorFilter c){}','setColorFilter(ColorMatrixColorFilter c){colorFilter=c;}')
paint = paint.replace('setXfermode(PorterDuffXfermode x){}','setXfermode(PorterDuffXfermode x){xfer=x;}')
sources['android/graphics/Paint.java'] = paint
canvas = sources['android/graphics/Canvas.java'].replace('public class Canvas{', '''public class Canvas{
 public static final java.util.List<Boolean> filtering=new java.util.ArrayList<>();
 private void sample(Paint p){filtering.add(p.filtered);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
 p.filtered?RenderingHints.VALUE_INTERPOLATION_BILINEAR:RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);}
 public void drawRoundRect(RectF r,float x,float y,Paint p){drawRect(r.left,r.top,r.right,r.bottom,p);}
''')
canvas = canvas.replace('drawBitmap(Bitmap b,float x,float y,Paint p){','drawBitmap(Bitmap b,float x,float y,Paint p){sample(p);')
canvas = canvas.replace('drawBitmap(Bitmap b,Object src,RectF dst,Paint p){','drawBitmap(Bitmap b,Object src,RectF dst,Paint p){sample(p);')
sources['android/graphics/Canvas.java'] = canvas
sources['android/graphics/Bitmap.java'] = sources['android/graphics/Bitmap.java'].replace('public void recycle()',
    'public void setPixels(int[]p,int o,int s,int x,int y,int w,int h){image.setRGB(x,y,w,h,p,o,s);}public void recycle()')
sources['android/graphics/Color.java'] = sources['android/graphics/Color.java'].replace('public static int WHITE=', 'public static int BLACK=0xff000000;public static int WHITE=')
sources['android/util/Log.java'] = 'package android.util;public class Log{public static int w(String t,String m,Throwable e){return 0;}}'
bridge = (menu/'org/openbw/androidtest/NativeBridge.java').read_text(encoding='utf-8')
bridge = bridge.replace('final class NativeBridge {','final class NativeBridge {static int value(int k,int i){return 0;}')
bridge = bridge.replace('movieOpen(byte[] b,boolean audio){throw new UnsupportedOperationException();}',
    'movieOpen(byte[] b,boolean audio){if(b.length!=1||b[0]!=87)throw new UnsupportedOperationException();return new int[]{1,2,2,8,42};}')
bridge = bridge.replace('movieFrame(int id,double t,boolean loop,Bitmap bitmap){}',
    'movieFrame(int id,double t,boolean loop,Bitmap bitmap){bitmap.setPixels(new int[]{-1,0xff000000,0xff000000,-1},0,2,0,0,2,2);}')
sources['org/openbw/androidtest/NativeBridge.java'] = bridge
hud = (src/'NativeHud.java').read_text(encoding='utf-8')
start = hud.index('  Bitmap panel('); end = hud.index('\n  void draw(', start)
sources['org/openbw/androidtest/HudPanel.java'] = '''package org.openbw.androidtest;
import android.graphics.*;import java.util.*;class HudPanel{
final Map<String,Bitmap> panels=new HashMap<>();byte[] dialogGrp;int[] palette;
''' + hud[start:end] + '\n}'
files = []
for name, content in sources.items():
    path = out/name; path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8'); files.append(path)
real = [src/n for n in ['NativeArt.java','NativeGlue.java','NativePortrait.java','GameLanguage.java','PortText.java','ClassicFontCipher.java','MenuLabels.java']]
subprocess.run(['javac','-encoding','UTF-8','-d',str(out/'classes'),*map(str,files+real),str(root/'tests/Graphics87Test.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',str(out/'classes'),'org.openbw.androidtest.Graphics87Test',str(menu/'assets'),str(out)],check=True)
(out/'audit.json').write_text(json.dumps({'bitmapDrawSites':calls,'physicalAndroidTested':False,
    'sourceSha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in real+[src/'NativeSurface.java',src/'NativeHud.java']}},indent=2)+'\n',encoding='utf-8')
