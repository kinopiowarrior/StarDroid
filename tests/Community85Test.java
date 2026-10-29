package org.openbw.androidtest;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

public final class Community85Test {
  static int checks;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static byte[] table(String text,Charset encoding)throws Exception{
    byte[] names=text.getBytes(encoding);int count=228,offset=2+2*count;
    byte[] out=new byte[offset+names.length+1];out[0]=(byte)count;out[1]=(byte)(count>>8);
    for(int i=0;i<count;i++){out[2+2*i]=(byte)offset;out[3+2*i]=(byte)(offset>>8);}
    System.arraycopy(names,0,out,offset,names.length);return out;
  }
  public static void main(String[] args)throws Exception{
    check(GameLanguage.detect(null).equals("en"),"Missing archive fallback");
    check(GameLanguage.detect(new byte[]{-1,-1,0,0,0,0}).equals("en"),"Invalid table accepted");
    String[][] cases={
      {"en","Terran Marine\nTerran Goliath\nProtoss Zealot\nZerg Drone"},
      {"es","Marine terran\nFantasma\nBuitre\nTanque de asedio\nCrucero de batalla\nSonda\nHidralisco"},
      {"de","Terranischer Goliath\nFeuerfresser\nProtoss Berserker\nZerg Drohne\nBelagerungspanzer"},
      {"fr","Marine\nFantôme\nVautour\nChar de siège\nZélote\nCouveuse"},
      {"ru","Морпех\nПризрак\nТрутень\nЗонд\nГидралиск"}
    };
    for(String[] item:cases){byte[] b=table(item[1],item[0].equals("ru")?GameLanguage.RUSSIAN:GameLanguage.WESTERN);
      check(GameLanguage.detect(b).equals(item[0]),"Wrong detected language: "+item[0]);
      check(GameLanguage.isRussian(b)==item[0].equals("ru"),"Western data treated as Cyrillic: "+item[0]);
    }
    PortText.load(Files.newInputStream(Paths.get(args[0])));
    for(String code:new String[]{"ru","de","es","fr"}){PortText.select(code);
      for(String label:new String[]{"Automatic (game files)","Command buttons","Control groups","Smooth game graphics","The New Dominion"})
        check(!PortText.text(label).equals(label),"Untranslated "+code+": "+label);
    }
    PortText.select("es");check(PortText.text("Command buttons: 110%").endsWith(": 110%"),"Size label lost value");
    PortText.select("en");check(PortText.text("The New Dominion").equals("The New Dominion"),"English override ignored");
    for(int i=1;i<args.length;i++)System.out.println("Archive "+Paths.get(args[i]).getFileName()+": "+GameLanguage.detect(Files.readAllBytes(Paths.get(args[i]))));
    System.out.println("PASS "+checks+" language/label checks: five languages, malformed tables, accents, overrides and new options");
  }
}
