package org.openbw.androidtest;

import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Synthetic round-trip checks for the mission-export utility; requires no game assets. */
public final class NativeMissionExportInstrumentation extends NativeInstrumentation {
  void pass(String name) throws Exception { report.put(name, true); save(); }
  byte[] map = new byte[8192], voice = new byte[16384];

  int hash(String name, int kind) throws Exception {
    Field f = NativeData.class.getDeclaredField("CRYPT"); f.setAccessible(true);
    int[] table = (int[]) f.get(null); int a = 0x7fed7fed, b = 0xeeeeeeee;
    for (char c : name.toUpperCase(Locale.ROOT).toCharArray()) {
      a = table[kind * 256 + c] ^ (a + b); b = c + a + b + (b << 5) + 3;
    }
    return a;
  }
  byte[] encrypt(byte[] data, String name) throws Exception {
    Method m = NativeData.class.getDeclaredMethod("cipher", byte[].class, int.class, boolean.class);
    m.setAccessible(true); return (byte[]) m.invoke(null, data, hash(name, 3), true);
  }
  void put(byte[] data, int offset, int value) { for (int i=0;i<4;i++) data[offset+i]=(byte)(value >>> (i*8)); }

  File fixture(File directory, String id) throws Exception {
    String scenario = CampaignCatalog.prefix(id) + "staredit\\scenario.chk";
    String wav = CampaignCatalog.prefix(id) + "staredit\\wav\\test.wav";
    byte[] header = new byte[32], hashes = new byte[128], blocks = new byte[32];
    Arrays.fill(hashes, (byte)255);
    header[0]='M';header[1]='P';header[2]='Q';header[3]=26;
    put(header,4,32); put(header,8,4096+map.length+voice.length); header[14]=3;
    put(header,16,32); put(header,20,160); put(header,24,8); put(header,28,2);
    int offset=4096, index=0;
    for(String name:new String[]{scenario,wav}) {
      int slot=hash(name,0)&7;
      while(NativeData.u32(hashes,slot*16+12)!=0xffffffffL)slot=(slot+1)&7;
      put(hashes,slot*16,hash(name,1));put(hashes,slot*16+4,hash(name,2));
      put(hashes,slot*16+8,0);put(hashes,slot*16+12,index);
      int length=index==0?map.length:voice.length;
      put(blocks,index*16,offset);put(blocks,index*16+4,length);put(blocks,index*16+8,length);
      put(blocks,index*16+12,0x81000000);offset+=length;index++;
    }
    File mpq=File.createTempFile("fixture-", ".mpq", output);
    File bundle=new File(directory,CampaignData.filename(id));
    try {
      try(RandomAccessFile out=new RandomAccessFile(mpq,"rw")) {
        out.write(header);out.write(encrypt(hashes,"(hash table)"));out.write(encrypt(blocks,"(block table)"));
        out.seek(4096);out.write(map);out.write(voice);
      }
      try(FileInputStream in=new FileInputStream(mpq);FileOutputStream out=new FileOutputStream(bundle)) {
        MissionBundle.export(in.getChannel(),Arrays.asList(scenario,wav),scenario,out,message->{});
      }
      check(id.equals(CampaignData.validate(bundle)),"Fixture invalid");
      return bundle;
    } finally {mpq.delete();}
  }

  byte[] read(ZipFile zip,String name) throws Exception {return NativeData.read(zip.getInputStream(zip.getEntry(name)),8*1024*1024);}
  @Override public void onStart() {
    Bundle result=new Bundle();
    try {
      output=new File(getTargetContext().getExternalFilesDir(null),"native-mission-export");output.mkdirs();
      recordPackageHashes();report.put("version",getTargetContext().getPackageManager().getPackageInfo("org.openbw.androidtest",0).versionName);report.put("android",Build.VERSION.RELEASE);
      report.put("abi",Build.SUPPORTED_ABIS[0]);report.put("originalGameAssets",false);
      new Random(31).nextBytes(map);new Random(32).nextBytes(voice);
      File inputs=new File(output,"inputs"),cache=new File(output,"cache");inputs.mkdirs();cache.mkdirs();
      for(File f:inputs.listFiles())f.delete();for(File f:cache.listFiles())f.delete();
      try {MissionExport.create(inputs,cache);throw new Exception("Empty export accepted");}
      catch(IOException expected){check(expected.getMessage().contains("No mission"),"Empty export error unclear");}
      pass("emptyExportRejected");
      Map<String,String> before=new HashMap<String,String>();
      for(Object[] mission:CampaignCatalog.ALL){File f=fixture(inputs,(String)mission[1]);before.put(f.getName(),sha256(f.getPath()));}
      // Unrelated files must never be copied into the upload.
      try(FileOutputStream out=new FileOutputStream(new File(inputs,"personal-save.txt"))){out.write(123);}
      MissionExport.Result export=MissionExport.create(inputs,cache);
      check(export.count==64,"Not all campaign maps exported");
      try(ZipFile outer=new ZipFile(export.file)) {
        JSONObject listing=new JSONObject(new String(read(outer,"manifest.json"),"UTF-8"));
        check(outer.size()==66&&listing.getJSONArray("notInstalled").length()==0,"Unexpected export contents");
        check(listing.getBoolean("mapsOnly")&&listing.getJSONArray("missions").length()==64,"Wrong manifest");
        JSONArray missions=listing.getJSONArray("missions");
        for(int i=0;i<missions.length();i++) {
          JSONObject item=missions.getJSONObject(i);String id=item.getString("mission");
          File compact=new File(cache,"roundtrip.zip");
          try(FileOutputStream out=new FileOutputStream(compact)){out.write(read(outer,item.getString("file")));}
          check(sha256(compact.getPath()).equals(item.getString("sha256")),"Export checksum wrong");
          check(CampaignData.validate(compact).equals(id),"Map bundle invalid");
          try(ZipFile inner=new ZipFile(compact);ZipFile original=new ZipFile(new File(inputs,CampaignData.filename(id)))) {
            check(inner.size()==5&&inner.getEntry("blocks/1.bin")==null,"Voice or unrelated entry leaked");
            check(Arrays.equals(read(inner,"blocks/0.bin"),map),"Map bytes changed");
            for(String name:new String[]{"header.bin","hash-table.bin","block-table.bin"})
              check(Arrays.equals(read(inner,name),read(original,name)),"MPQ metadata changed");
            JSONObject manifest=new JSONObject(new String(read(inner,"manifest.json"),"UTF-8"));
            check(manifest.getJSONArray("entries").length()==1,"Voice manifest entry leaked");
          }
          File rebuilt=NativeData.prepare(compact,cache,"roundtrip");
          try(RandomAccessFile in=new RandomAccessFile(rebuilt,"r")) {
            in.seek(4096);byte[] actual=new byte[map.length];in.readFully(actual);check(Arrays.equals(actual,map),"Map reconstruction failed");
            in.seek(4096+map.length);byte[] removed=new byte[voice.length];in.readFully(removed);
            check(Arrays.equals(removed,new byte[voice.length]),"Voice payload reconstructed unexpectedly");
          }
          compact.delete();rebuilt.delete();new File(cache,"roundtrip.stamp").delete();
        }
      }
      report.put("exportedMaps",64);report.put("allMapsExportBytes",export.file.length());export.file.delete();
      for(Map.Entry<String,String> original:before.entrySet())check(original.getValue().equals(sha256(new File(inputs,original.getKey()).getPath())),"Installed mission changed");
      pass("all64MapsRoundTripWithOriginalMetadata");pass("voicesAndUnrelatedFilesExcluded");pass("installedMissionsUnchanged");
      for(Object[] mission:CampaignCatalog.ALL)if(!mission[1].equals("zerg03")&&!mission[1].equals("expzerg05"))new File(inputs,CampaignData.filename((String)mission[1])).delete();
      export=MissionExport.create(inputs,cache);
      try(ZipFile zip=new ZipFile(export.file)) {
        check(export.count==2&&new JSONObject(new String(read(zip,"manifest.json"),"UTF-8")).getJSONArray("notInstalled").length()==62,"Partial install not listed correctly");
      }
      export.file.delete();pass("partialInstallExportsAvailableMaps");
      File broken=new File(inputs,CampaignData.filename("zerg03"));byte[] original=NativeData.read(new FileInputStream(broken),1024*1024);
      try(FileOutputStream out=new FileOutputStream(broken)){out.write(0);}
      try {MissionExport.create(inputs,cache);throw new Exception("Damaged mission accepted");}
      catch(IOException expected){check(expected.getMessage().contains("The New Dominion"),"Damaged mission not named");}
      check(cache.listFiles().length==0,"Temporary export files leaked after failure");
      try(FileOutputStream out=new FileOutputStream(broken)){out.write(original);}
      pass("corruptMissionRejectedAndTemporaryFilesRemoved");
      report.put("passed",true);save();result.putString("result",report.toString());finish(-1,result);
    } catch(Throwable error) {
      try{report.put("passed",false);report.put("error",android.util.Log.getStackTraceString(error));save();}catch(Exception ignored){}
      result.putString("error",android.util.Log.getStackTraceString(error));finish(0,result);
    }
  }
}
