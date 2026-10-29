package org.openbw.androidtest;

import android.graphics.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Production bitmap rendering with a deterministic AWT Canvas stand-in. */
public class Graphics87Test {
  static int checks;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static boolean same(Bitmap a,Bitmap b){
    if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return false;
    for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)
      if(a.getPixel(x,y)!=b.getPixel(x,y))return false;
    return true;
  }
  static void sampling(boolean smooth,String name){
    check(!Canvas.filtering.isEmpty(),name+" did not draw");
    check(!Canvas.filtering.contains(!smooth),name+" escaped the selected sampling mode");
  }
  static Bitmap mainMenu(NativeGlue glue,boolean smooth){
    NativeArt.setSmoothGraphics(smooth);Canvas.filtering.clear();
    Bitmap b=Bitmap.createBitmap(960,720,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.scale(1.5f,1.5f);
    glue.elapsed=1000;glue.draw(c,0);glue.label(c,3,null,true);sampling(smooth,"main menu");return b;
  }
  public static void main(String[] args)throws Exception{
    NativeBridge.root=Paths.get(args[0]);Path out=Paths.get(args[1]);
    check(NativeArt.PIXELS.isFilterBitmap(),"Graphics must start smooth");
    NativeArt art=new NativeArt();NativeGlue glue=new NativeGlue(art,"glumain","palmm",false);
    Bitmap sharp=mainMenu(glue,false),smooth=mainMenu(glue,true),sharpAgain=mainMenu(glue,false);
    check(!same(sharp,smooth),"Menu pixels do not respond to filtering");
    check(same(sharp,sharpAgain),"Menu toggle is not reversible");
    ImageIO.write(sharp.image,"png",out.resolve("menu-sharp.png").toFile());
    ImageIO.write(smooth.image,"png",out.resolve("menu-smooth.png").toFile());

    Bitmap buttonSharp=glue.panel(101,31,true),selectorSharp=glue.selectFrame(101,31);
    NativeArt.setSmoothGraphics(true);
    Bitmap buttonSmooth=glue.panel(101,31,true),selectorSmooth=glue.selectFrame(101,31);
    check(buttonSharp!=buttonSmooth&&selectorSharp!=selectorSmooth,"Menu reuses wrong-mode button cache");
    check(!same(buttonSharp,buttonSmooth),"Resized menu button pixels did not change");
    NativeArt.setSmoothGraphics(false);
    check(glue.panel(101,31,true)==buttonSharp&&glue.selectFrame(101,31)==selectorSharp,"Sharp cache lost");

    // All GRP frame indices point at the same tiny raw checkerboard tile.
    HudPanel hud=new HudPanel();hud.palette=new int[]{0,-1,0xff000000};hud.dialogGrp=new byte[6+118*8+4];
    hud.dialogGrp[0]=118;hud.dialogGrp[2]=hud.dialogGrp[4]=2;int data=6+118*8;
    for(int i=0;i<118;i++){int p=6+i*8;hud.dialogGrp[p+2]=hud.dialogGrp[p+3]=2;
      for(int j=0;j<4;j++)hud.dialogGrp[p+4+j]=(byte)(data>>(j*8));}
    hud.dialogGrp[data]=hud.dialogGrp[data+3]=1;hud.dialogGrp[data+1]=hud.dialogGrp[data+2]=2;
    Bitmap hudSharp=hud.panel(101,31,true,true);NativeArt.setSmoothGraphics(true);
    Bitmap hudSmooth=hud.panel(101,31,true,true);
    check(hudSharp!=hudSmooth&&!same(hudSharp,hudSmooth),"HUD button retains sharp cached scaling");
    NativeArt.setSmoothGraphics(false);check(hud.panel(101,31,true,true)==hudSharp,"HUD cache is not reversible");

    byte[] font=new byte[20];System.arraycopy("FONT".getBytes("US-ASCII"),0,font,0,4);
    font[4]=font[5]=65;font[6]=font[7]=2;font[8]=12;font[12]=font[13]=2;
    font[16]=1;font[17]=2;font[18]=2;font[19]=1;
    NativeArt.Font small=new NativeArt.Font(font,glue.tint,glue.background.palette,1,2);
    NativeArt.Font resized=new NativeArt.Font(small,7,1);
    Bitmap glyphSharp=resized.glyph(65,2);int width=resized.measure("AAA");
    NativeArt.setSmoothGraphics(true);Bitmap glyphSmooth=resized.glyph(65,2);
    check(glyphSharp!=glyphSmooth&&!same(glyphSharp,glyphSmooth),"Resized font retained old sampling");
    check(resized.measure("AAA")==width,"Graphics setting changed text layout");
    NativeArt.setSmoothGraphics(false);check(same(glyphSharp,resized.glyph(65,2)),"Font toggle not reversible");

    NativeArt.Movie movie=new NativeArt.Movie(new byte[]{87});Paint effects=new Paint();effects.setAlpha(73);
    Object tint=new ColorMatrixColorFilter(new float[20]),mask=new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    effects.setColorFilter((ColorMatrixColorFilter)tint);effects.setXfermode((PorterDuffXfermode)mask);
    byte[] portraits=new byte[12];portraits[0]=portraits[4]=portraits[8]=1;
    art.bytes.put("portrait/test0.smk",new byte[]{87});NativePortrait portrait=new NativePortrait(art,portraits,new String[]{"test"});
    portrait.target(0,false);portrait.selection(7);
    for(boolean mode:new boolean[]{true,false,true}){
      NativeArt.setSmoothGraphics(mode);Bitmap target=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(target);
      Canvas.filtering.clear();movie.draw(c,0,0,93,91,0,true,effects);sampling(mode,"animated menu/cinematic");
      check(effects.alpha==73&&effects.colorFilter==tint&&effects.xfer==mask,"Filtering damaged animation effects");
      Canvas.filtering.clear();portrait.draw(c,0,0,93,91,0,false,false);sampling(mode,"unit portrait and static");
      Canvas.filtering.clear();portrait.draw(c,0,0,93,91,200,false,true);sampling(mode,"briefing portrait");
      check(NativeArt.bitmapPaint(effects)==effects&&effects.filtered==mode&&effects.xfer==mask,"Briefing mask lost blend mode");
    }
    movie.close();portrait.close();glue.close();
    System.out.println("PASS "+checks+" graphics checks: reversible menu pixels, button/font caches, animated/static portraits, movie and mask sampling/effects. Host AWT rendering; physical Android untested.");
  }
}
