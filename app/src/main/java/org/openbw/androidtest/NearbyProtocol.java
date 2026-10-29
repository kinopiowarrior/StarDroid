package org.openbw.androidtest;

import java.io.*;
import java.util.*;

/** Bounded, versioned messages. No device identity, paths, or executable content. */
final class NearbyProtocol {
  static final int BUILD = 87, VERSION = 2, MAX_BODY = 16384, MAX_MAP = 8 * 1024 * 1024;
  static final int FRAME_MS=42, TURN_FRAMES=4, TURN_MS=FRAME_MS*TURN_FRAMES, MAP_WINDOW=8, MAP_ACK_CHUNKS=4;
  static final int HELLO=1, CONFIG=2, CHUNK=3, ACK=4, READY=5, START=6, TURN=7, PAUSE=8, PING=9, LEAVE=10, ERROR=11, FINISH=12, PONG=13, STATE_META=14, STATE_CHUNK=15, STATE_ACK=16, STATE_READY=17, COUNTDOWN=18, RELOAD=19;
  static final byte[] EMPTY = new byte[0];
  static final class Message {
    final int type, number; final long hash; final byte[] data;
    Message(int t,int n,long h,byte[] d) {type=t;number=n;hash=h;data=d;}
  }
  static byte[] pack(int type,int number,long hash,byte[] data) throws IOException {
    if(data==null || data.length>MAX_BODY || type<HELLO || type>RELOAD)throw new IOException("Invalid nearby message");
    ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream o=new DataOutputStream(b);
    o.writeInt(0x4f42574e);o.writeByte(VERSION);o.writeByte(type);o.writeInt(number);o.writeLong(hash);o.writeInt(data.length);o.write(data);return b.toByteArray();
  }
  static Message parse(byte[] bytes) throws IOException {
    if(bytes==null || bytes.length<22 || bytes.length>MAX_BODY+22)throw new IOException("Invalid nearby packet size");
    DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
    if(in.readInt()!=0x4f42574e || in.readUnsignedByte()!=VERSION)throw new IOException("Both devices need the same app version");
    int type=in.readUnsignedByte(), number=in.readInt();long hash=in.readLong();int size=in.readInt();
    if(type<HELLO || type>RELOAD || size<0 || size!=in.available())throw new IOException("Damaged nearby packet");
    byte[] data=new byte[size];in.readFully(data);return new Message(type,number,hash,data);
  }
  static final class Turns {
    private final TreeMap<Integer,Message> pending=new TreeMap<Integer,Message>();
    void put(Message m,int current) throws IOException {
      if(m.type!=TURN || m.number<current || m.number>current+2)throw new IOException("Unexpected multiplayer turn");
      Message old=pending.get(m.number);
      if(old!=null && (old.hash!=m.hash || !Arrays.equals(old.data,m.data)))throw new IOException("Conflicting multiplayer turn");
      pending.put(m.number,m);
    }
    Message take(int number) {return pending.remove(number);}
    void clear(){pending.clear();}
    Message peek(int number){return pending.get(number);}
    boolean has(int number) {return pending.containsKey(number);}
  }
  static String digest(byte[] bytes) throws Exception {
    StringBuilder s=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
  }
  static String name(String text) {
    if(text==null)return "Player";
    String s=text.replaceAll("[\\p{Cntrl}]", "").trim();
    return s.isEmpty()?"Player":s.substring(0,Math.min(32,s.length()));
  }
}
