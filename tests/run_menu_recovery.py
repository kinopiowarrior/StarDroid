#!/usr/bin/env python3
"""Fault-inject real menu loading and action routing; render through an AWT shim.

Usage: python3 tests/run_menu_recovery.py /path/to/classic-game-data
Optional --baseline DIR points at unmodified 1.0.1 NativeArt/NativeGlue sources.
Game assets stay in the ignored build directory and are never distributed.
"""
import argparse, ast, hashlib, json, os, re, subprocess
from pathlib import Path

r = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('data', type=Path)
p.add_argument('--baseline', type=Path)
a = p.parse_args()
b = r/'build-native/menu76'
b.mkdir(parents=True, exist_ok=True)
assets = b/'assets'
src = r/'app/src/main/java/org/openbw/androidtest'
extract = b/'extract-interface'
subprocess.run(['g++', '-std=c++17', '-O0', '-I'+str(r/'native-engine'),
                str(r/'tests/extract_interface46.cpp'), '-o', str(extract)], check=True)
names = {'rez/'+n+'.bin' for n in ['glumain','glucmpgn','gluexpcmpgn','glulogin']}
names |= {'glue/'+pal+'/'+n for pal in ['palmm','palcs','palnl']
          for n in ['backgnd.pcx','tfont.pcx','teffect.pcx','dlg.grp','tile.grp']}
names |= {prefix+'font'+size+'.fnt' for prefix in ['files/font/','font/']
          for size in ['8','10','12','14','16','16x','32','50']}

def get(name):
    found = []
    for slot, archive in [(0,'STARDAT.MPQ'),(1,'BROODAT.MPQ'),(2,'patch_rt.mpq')]:
        target = assets/str(slot)/name
        target.parent.mkdir(parents=True, exist_ok=True)
        call = subprocess.run([str(extract),str(a.data/archive),name,str(target)], capture_output=True)
        if call.returncode == 0:
            found.append(target.read_bytes())
        else:
            target.unlink(missing_ok=True)
    return found

more = set()
for name in sorted(names):
    for data in get(name):
        if name.endswith('.bin'):
            more |= {s.decode('cp1252').replace('\\','/').lower()
                     for s in re.findall(rb'(?:glue|GLUE)[^\x00]*?\.[pP][cC][xX]',data)}
for name in sorted(more - names):
    get(name)

tree = ast.parse((r/'tests/run_results46.py').read_text())
sources = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign)
               and any(isinstance(t,ast.Name) and t.id == 'sources' for t in n.targets))
sources.pop('org/openbw/androidtest/Results46Harness.java')
sources['android/graphics/Typeface.java'] = '''package android.graphics;public class Typeface{
 public static final int NORMAL=0,BOLD=1;public int style;public String family="sans-serif";
 public static Typeface DEFAULT=new Typeface(),MONOSPACE=new Typeface();
 public static Typeface create(String family,int style){Typeface t=new Typeface();t.family=family;t.style=style;return t;}}'''
sources['android/graphics/Rect.java'] = '''package android.graphics;public class Rect{
 public int left,top,right,bottom;public int width(){return right-left;}public int height(){return bottom-top;}}'''
sources['android/graphics/Paint.java'] = sources['android/graphics/Paint.java'].replace('public class Paint{','public class Paint{public boolean filtered;public boolean isFilterBitmap(){return filtered;}public void setFilterBitmap(boolean f){filtered=f;}public void setDither(boolean d){}').replace(
 'public void setTypeface(Typeface t){}', '''public Typeface typeface=Typeface.DEFAULT;public float scaleX=1;
 public void setTypeface(Typeface t){typeface=t;}public void setTextScaleX(float x){scaleX=x;}
 public float getTextSize(){return size;}''').replace('public void setColor(int c)', '''
 public static class FontMetrics{public float ascent,descent;}
 public java.awt.Font font(){return new java.awt.Font("SansSerif",typeface.style,1).deriveFont(size)
   .deriveFont(java.awt.geom.AffineTransform.getScaleInstance(scaleX,1));}
 public java.awt.font.FontRenderContext frc(){return new java.awt.font.FontRenderContext(null,true,true);}
 public float measureText(String text){return (float)font().getStringBounds(text,frc()).getWidth();}
 public void getTextBounds(String text,int start,int end,Rect bounds){java.awt.Rectangle b=font()
   .createGlyphVector(frc(),text.substring(start,end)).getPixelBounds(frc(),0,0);
   bounds.left=b.x;bounds.top=b.y;bounds.right=b.x+b.width;bounds.bottom=b.y+b.height;}
 public FontMetrics getFontMetrics(){java.awt.font.LineMetrics l=font().getLineMetrics("Mg",frc());
   FontMetrics m=new FontMetrics();m.ascent=-l.getAscent();m.descent=l.getDescent();return m;}
 public void setColor(int c)''')
sources['android/graphics/Canvas.java'] = sources['android/graphics/Canvas.java'].replace(
 'new Font("Monospaced",Font.PLAIN,Math.round(p.size))','p.font()').replace(
 'g.drawString(t,x,y);','g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.drawString(t,x,y);')

sources['android/graphics/Canvas.java'] = sources['android/graphics/Canvas.java'].replace('public void translate(', 'public void scale(float x,float y){g.scale(x,y);}public void translate(')
sources['org/openbw/androidtest/NativeBridge.java'] = r'''package org.openbw.androidtest;
import android.graphics.*;import java.nio.file.*;import java.util.*;
final class NativeBridge {
 static byte[] archiveText(String path){return null;}
 static Path root;static Map<String,byte[]> overrides=new HashMap<>();static Set<String> errors=new HashSet<>();
 static byte[] asset(int slot,String name){
  if(slot<0){for(int i:new int[]{2,1,0,4}){byte[] data=asset(i,name);if(data!=null)return data;}return null;}
  String key=slot+"/"+name;if(errors.contains(key))throw new IllegalArgumentException("synthetic MPQ decode error");
  if(overrides.containsKey(key))return overrides.get(key);
  try{return Files.readAllBytes(root.resolve(key));}catch(Exception missing){return null;}
 }
 static int[] movieOpen(byte[] b,boolean audio){throw new UnsupportedOperationException();}
 static int[] movieOpenAsset(int slot,String path,boolean audio){throw new UnsupportedOperationException();}
 static void movieFrame(int id,double t,boolean loop,Bitmap bitmap){}static void movieClose(int id){}
}'''
sources['org/openbw/androidtest/MenuRecoveryTest.java'] = r'''package org.openbw.androidtest;
import android.graphics.*;import java.nio.file.*;import java.util.*;import javax.imageio.ImageIO;
public class MenuRecoveryTest {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void reset(){NativeBridge.overrides.clear();NativeBridge.errors.clear();}
 static void render(NativeGlue glue,Path output,int[] ids)throws Exception {
  glue.elapsed=700;Bitmap bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
  glue.draw(canvas,0);for(int id:ids)glue.label(canvas,id,null,true);ImageIO.write(bitmap.image,"png",output.toFile());glue.close();
 }
 static byte[] rawPcx(){byte[] b=new byte[901];b[0]=10;b[1]=5;b[3]=8;b[8]=1;b[10]=1;b[65]=1;b[66]=2;
  b[128]=1;b[129]=(byte)193;b[130]=2;b[131]=3;b[132]=12;b[136]=127;return b;}
 static byte[] font(){byte[] b=new byte[18];b[0]='F';b[1]='O';b[2]='N';b[3]='T';b[4]=b[5]=65;b[6]=1;b[7]=10;b[8]=12;b[12]=1;b[13]=1;b[16]=1;return b;}
 public static void main(String[] args)throws Exception {
  NativeBridge.root=Paths.get(args[0]);Path out=Paths.get(args[1]);
  NativeArt original=new NativeArt();NativeGlue main=new NativeGlue(original,"glumain","palmm",false);
  check(main.layout.get(3)!=null&&main.layout.get(4)!=null,"Original main actions unavailable");
  render(main,out.resolve("main-normal.png"),new int[]{3,5,2,9,4,8});
  // Real core archives lack the installation CD's campaign palette.
  for(String name:new String[]{"glucmpgn","gluexpcmpgn"}) {
   NativeGlue campaign=new NativeGlue(new NativeArt(),name,"palcs",true);
   check(campaign.paletteName.equals("palnl")&&!campaign.recovery.isEmpty(),"Missing campaign palette did not recover");
   for(int id:new int[]{6,7,8,9,5,11,10})check(campaign.layout.get(id)!=null,"Lost campaign action "+id);
   render(campaign,out.resolve(name+"-recovered.png"),new int[]{6,7,8,9,5,11,10});
  }
  // A re-imported palette must win over the neutral recovery theme.
  NativeBridge.overrides.put("4/glue/palcs/backgnd.pcx",Files.readAllBytes(NativeBridge.root.resolve("0/glue/palnl/backgnd.pcx")));
  NativeBridge.overrides.put("4/glue/palcs/tfont.pcx",Files.readAllBytes(NativeBridge.root.resolve("0/glue/palnl/tfont.pcx")));
  NativeGlue restored=new NativeGlue(new NativeArt(),"glucmpgn","palcs",true);
  check(restored.paletteName.equals("palcs")&&!restored.recovery.contains("Missing or unreadable asset"),"Valid imported palette not restored");
  check(restored.artworkRecovery.isEmpty()&&restored.recovery.contains("Styled Android"),"Font fallback should be diagnostic only when artwork loads");restored.close();reset();
  byte[] good=Files.readAllBytes(NativeBridge.root.resolve("0/glue/palmm/backgnd.pcx"));
  NativeBridge.overrides.put("2/glue/palmm/backgnd.pcx",new byte[]{1,2,3});
  NativeArt art=new NativeArt();NativeArt.PCX recovered=art.pcx("glue/palmm/backgnd.pcx",false,-1);
  NativeArt.PCX expected=new NativeArt.PCX(good,false,-1);
  check(Arrays.equals(recovered.pixels,expected.pixels),"Damaged patch hides usable core artwork");
  check(art.diagnostics().contains("Patch_rt.mpq: glue/palmm/backgnd.pcx"),"Rejected asset/source not named");
  check(recovered==art.pcx("glue/palmm/backgnd.pcx",false,-1),"Validated image not cached");
  reset();NativeBridge.errors.add("2/glue/palmm/backgnd.pcx");
  check(Arrays.equals(new NativeArt().pcx("glue/palmm/backgnd.pcx",false,-1).pixels,expected.pixels),"MPQ exception prevents alternate source");
  reset();NativeBridge.overrides.put("2/rez/glumain.bin",new byte[100]);
  check(new NativeArt().dialog("glumain").get(3)!=null,"Invalid/empty patch dialog hides usable core layout");
  reset();byte[] goodDialog=Files.readAllBytes(NativeBridge.root.resolve("0/rez/glumain.bin"));
  byte[] overflow=goodDialog.clone();Arrays.fill(overflow,66,70,(byte)127);
  NativeBridge.overrides.put("2/rez/glumain.bin",overflow);
  check(new NativeArt().dialog("glumain").get(3)!=null,"Out-of-bounds dialog hides good layout");
  reset();NativeBridge.overrides.put("2/files/font/font10.fnt",new byte[]{1,2,3});
  NativeBridge.overrides.put("4/files/font/font10.fnt",font());
  art=new NativeArt();NativeArt.PCX tint=art.pcx("glue/palmm/tfont.pcx",false,-1);
  NativeArt.Font font=art.font("10",tint,expected.palette,1);
  check(!font.fallback&&font.low==65,"Invalid core font hides valid imported font");
  NativeArt.Font borrowed=art.font("14",tint,expected.palette,1);
  check(!borrowed.fallback&&borrowed.substitute!=null&&borrowed.height==14,"Missing size did not reuse available original font");
  check(borrowed.glyph(65,2).getHeight()==14&&borrowed.advance(65)>0,"Borrowed font was not scaled");
  check(borrowed.withColor(0xffcc8844).glyph(65,2).getHeight()==14,"Recolored original substitute lost size");
  check(art.diagnostics().contains("ORIGINAL SUBSTITUTE: font10.fnt"),"Borrowed font source not reported");
  reset();NativeBridge.overrides.put("1/font/font12.fnt",font());
  NativeArt optionalArt=new NativeArt();
  check(optionalArt.font("16x",tint,expected.palette,1).substitute!=null,"Non-menu original font12 was ignored");
  check(optionalArt.diagnostics().contains("BrooDat.mpq")&&optionalArt.diagnostics().contains("font12.fnt"),"Borrowed optional font archive missing from report");
  NativeBridge.overrides.put("4/font/font16x.fnt",font());
  check(new NativeArt().font("16x",tint,expected.palette,1).substitute==null,"A different original size overrode exact imported font");
  reset();for(int slot:new int[]{0,1,2,4})for(String prefix:new String[]{"files/font/","font/"})
   NativeBridge.overrides.put(slot+"/"+prefix+"font10.fnt",new byte[]{0});
  check(new NativeArt().font("10",tint,expected.palette,1).fallback,"All missing fonts should retain readable system text");
  NativeArt fallbackArt=new NativeArt(); NativeArt.Font fallbackFont=fallbackArt.font("10",tint,expected.palette,1);
  check(fallbackFont.measure("iii")<fallbackFont.measure("WWW"),"System font must use proportional advances");
  check(fallbackArt.diagnostics().contains("SYSTEM FALLBACK"),"Font substitution missing from report");
  check(art.diagnostics().contains("imported interface"),"Loaded font source missing from report");
  reset();NativeArt.PCX raw=new NativeArt.PCX(rawPcx(),false,-1);
  check(Arrays.equals(raw.pixels,new byte[]{1,(byte)193,2,3}),"Uncompressed PCX decoded as RLE");
  try{new NativeArt.PCX(Arrays.copyOf(rawPcx(),900),false,-1);throw new AssertionError("Truncated raw PCX accepted");}catch(IllegalArgumentException expectedError){checks++;}
  byte[] tooLarge=rawPcx();tooLarge[9]=127;
  try{new NativeArt.PCX(tooLarge,false,-1);throw new AssertionError("Huge PCX accepted");}catch(IllegalArgumentException expectedError){checks++;}
  reset();for(int slot:new int[]{0,1,2,4})NativeBridge.overrides.put(slot+"/glue/palnl/backgnd.pcx",null);
  try{new NativeGlue(new NativeArt(),"glucmpgn","palcs",true);throw new AssertionError("Missing every palette accepted");}
  catch(IllegalArgumentException expectedError){check(expectedError.getMessage().contains("glue/palnl/backgnd.pcx"),"Total failure lacks exact filename");}
  reset();FallbackActions.run();FontFallbackTest.run(out,tint,expected.palette);
  System.out.println("PASS "+checks+" asset checks plus 13 fallback button labels/actions; real core menus, missing palette, damaged patch, MPQ exception, imported font recovery, raw PCX and malformed bounds. Host Canvas shim; no physical device test.");
 }
}'''

surface = (src/'NativeSurface.java').read_text()
start = surface.index('  private void glueButton(')
end = surface.index('\n  private void message(',start)
button_method = surface[start:end]
start = surface.index('      if (page.equals("main")) {')
end = surface.index('      if (page.equals("registry"))',start)
main = surface[start:end]
start = surface.index('      if (page.equals("campaign")) {',end)
end = surface.index('      if (page.equals("briefing"))',start)
campaign = surface[start:end]
sources['org/openbw/androidtest/FallbackActions.java'] = r'''package org.openbw.androidtest;
import android.graphics.*;import java.util.*;
class FallbackActions {
 Paint paint=new Paint(); void fallback(Canvas c,String t,float x,float y,int size,int color){}
 String page,modal="",action="";int race=-1;boolean registryForMultiplayer,multiplayerSetup,multiplayerSaves;
 NativeGlue glue;Runnable pendingScreen;List<Button> buttons=new ArrayList<>();Activity activity=new Activity();
 class Activity {void runOnUiThread(Runnable r){r.run();}void requestAppOptions(){action="options";}
  void requestExit(){action="exit";}void requestDiagnostics(){action="diagnostics";}}
 static class Button {String label;Runnable run;Button(String l,Runnable r){label=l;run=r;}}
 void button(Canvas c,String label,float x,float y,float w,float h,Runnable run,boolean enabled,boolean... navigation){
  if(y<0||y+h>480)throw new AssertionError("Button outside screen");buttons.add(new Button(label,run));}
 void add(RectF r,Runnable run,boolean enabled){throw new AssertionError("Not a fallback");}
 void navigate(Runnable run){run.run();}void showRegistry(){action="registry";}void playIntroduction(){action="intro";}
 void missionList(int r){race=r;}void openSaves(boolean saving){action="load";}void message(String s){action="message";}
 void showCustom(){action="custom";}void showReplays(){action="replays";}
 void queueMenuButtonVisual(RectF r,Runnable run,boolean enabled,boolean clickable,String label,boolean nav,java.util.function.Consumer<String> draw){draw.accept(label);buttons.add(new Button(label,run));}
''' + button_method + '\n void draw(Canvas c) {\n' + main + campaign + r'''}
 static void run(){FallbackActions h=new FallbackActions();
  h.page="main";h.draw(null);String[] main={"Single Player","App Options","Exit","Credits","Multiplayer","Play Introduction"};
  check(h.buttons.size()==6,"Main count");for(int i=0;i<6;i++)check(h.buttons.get(i).label.replaceAll("[\\x00-\\x1f]","").equals(main[i]),"Main label "+i);
  h.buttons.get(0).run.run();check(h.modal.equals("choose"),"Single Player action");h.buttons.get(1).run.run();check(h.action.equals("options"),"Import recovery action");
  h.buttons.get(2).run.run();check(h.action.equals("exit"),"Exit action");h.buttons.get(3).run.run();check(h.action.equals("diagnostics"),"Credits diagnostics action");
  h.buttons.get(4).run.run();check(h.registryForMultiplayer&&h.action.equals("registry"),"Multiplayer action");h.buttons.get(5).run.run();check(h.action.equals("intro"),"Intro action");
  h.buttons.clear();h.page="campaign";h.draw(null);String[] names={"Protoss Campaign","Terran Campaign","Zerg Campaign","Back","Load Game","Play Replay","Custom Game"};
  check(h.buttons.size()==7,"Campaign count");for(int i=0;i<7;i++)check(h.buttons.get(i).label.equals(names[i]),"Campaign label "+i);
  for(int i=0;i<3;i++){h.buttons.get(i).run.run();check(h.race==2-i,"Campaign race mismatch");}
  h.buttons.get(3).run.run();check(h.action.equals("registry"),"Back action");h.buttons.get(4).run.run();check(h.action.equals("load"),"Load action");
  h.buttons.get(5).run.run();check(h.action.equals("replays"),"Replay action");h.buttons.get(6).run.run();check(h.action.equals("custom"),"Custom action");
 }
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
}'''

sources['org/openbw/androidtest/FontFallbackTest.java'] = (r/'tests/FontFallbackTest.java').read_text()
for name, text in sources.items():
    target = b/name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)
real = [src/n for n in ['NativeArt.java','NativeGlue.java','MenuLabels.java','ClassicFontCipher.java','GameLanguage.java','PortText.java']]
classes = b/'classes'
subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),*[str(b/n) for n in sources],*map(str,real)],check=True)
result = subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes),
                         'org.openbw.androidtest.MenuRecoveryTest',str(assets),str(b)],capture_output=True,text=True)
print(result.stdout+result.stderr)
result.check_returncode()
palette_test = b/'org/openbw/androidtest/InterfacePaletteTest.java'
palette_test.write_text(r'''package org.openbw.androidtest;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;import org.json.*;
public class InterfacePaletteTest {
 static void write(File file,boolean palette,boolean complete)throws Exception {
  JSONObject m=new JSONObject().put("format","openbw-interface-bundle-v1");JSONArray entries=new JSONArray();
  List<String> names=new ArrayList<>();for(String n:InterfaceData.names())
   if((n.endsWith(".fnt")&&(!n.endsWith("50.fnt")||complete))||(palette&&(n.endsWith("backgnd.pcx")||n.endsWith("tfont.pcx"))))names.add(n);
  try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){
   for(String name:new String[]{"header.bin","hash-table.bin","block-table.bin"}){out.putNextEntry(new ZipEntry(name));out.write(new byte[name.equals("header.bin")?32:16]);out.closeEntry();}
   for(int i=0;i<names.size();i++){entries.put(new JSONObject().put("name",names.get(i)).put("block",i).put("packedSize",1).put("size",1));
    out.putNextEntry(new ZipEntry("blocks/"+i+".bin"));out.write(1);out.closeEntry();}
   out.putNextEntry(new ZipEntry("manifest.json"));out.write(m.put("entries",entries).toString().getBytes("UTF-8"));out.closeEntry();
  }
 }
 public static void main(String[] args)throws Exception {
  File f=new File(args[0]);write(f,false,true);InterfaceData.validate(f);
  if(InterfaceData.hasCampaignPalette(f))throw new AssertionError("Font-only bundle reported complete campaign artwork");
  write(f,true,true);InterfaceData.validate(f);if(!InterfaceData.hasCampaignPalette(f))throw new AssertionError("Complete palette not recognized");
  write(f,true,false);InterfaceData.validate(f);if(!InterfaceData.hasCampaignPalette(f)||!InterfaceData.missingMenuFiles(f).isEmpty())throw new AssertionError("Missing unused font discarded menu files");
  System.out.println("PASS interface metadata: font-only import distinguished from campaign palette; complete palette detected; missing unused font no longer discards menu files.");
 }
}''')
jar = r/'build-native/json-20240303.jar'
subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(src/'InterfaceData.java'),str(src/'MissionBundle.java'),str(src/'CampaignCatalog.java'),str(palette_test)],check=True)
palette = subprocess.run(['java','-cp',str(classes)+os.pathsep+str(jar),'org.openbw.androidtest.InterfacePaletteTest',
                          str(b/'interface-test.zip')],capture_output=True,text=True,check=True)
print(palette.stdout)
report = {'passed':True,'physicalAndroid':False,'version':'1.0.7','versionCode':77,
          'output':result.stdout.strip(),'interfaceImport':palette.stdout.strip(),
          'sourceSha256':{str(f.relative_to(r)):hashlib.sha256(f.read_bytes()).hexdigest()
          for f in real+[src/n for n in ['NativeSurface.java','InterfaceData.java','GameContent.java','MainActivity.java']]+[Path(__file__),r/'tests/FontFallbackTest.java']}}
if a.baseline:
    baseline = b/'baseline-classes'
    test = b/'org/openbw/androidtest/BaselineFailure.java'
    test.write_text('''package org.openbw.androidtest;import java.nio.file.*;
public class BaselineFailure {public static void main(String[]args){NativeBridge.root=Paths.get(args[0]);
 new NativeGlue(new NativeArt(),"glumain","palmm",false);
 try{new NativeGlue(new NativeArt(),"glucmpgn","palcs",true);throw new AssertionError("Baseline unexpectedly passed");}
 catch(IllegalArgumentException expected){System.out.println("REPRODUCED 1.0.1 campaign failure: "+expected.getMessage());}}}''')
    shim = [str(b/n) for n in sources if not n.endswith(('MenuRecoveryTest.java','FallbackActions.java','FontFallbackTest.java'))]
    subprocess.run(['javac','-encoding','UTF-8','-d',str(baseline),*shim,str(a.baseline/'NativeArt.java'),
                    str(a.baseline/'NativeGlue.java'),str(test)],check=True)
    old = subprocess.run(['java','-Djava.awt.headless=true','-cp',str(baseline),
                         'org.openbw.androidtest.BaselineFailure',str(assets)],capture_output=True,text=True,check=True)
    print(old.stdout)
    report['baselineReproduction'] = old.stdout.strip()
(r/'validation/menu-recovery-v1.0.7.json').write_text(json.dumps(report,indent=2)+'\n')
