package org.openbw.androidtest;
import java.io.*;
/** Every game message belongs to one lobby revision; old transfers cannot alter a new game. */
final class RoomProtocol {
  static final int BUILD=63, MAX=16384, HEADER=26;
  static final int HELLO=1,CONFIG=2,MAP_CHUNK=3,MAP_ACK=4,MAP_READY=5,STATE_CHUNK=6,STATE_ACK=7,READY=8,
    COUNTDOWN=9,START=10,INPUT=11,PAUSE=12,PING=13,PONG=14,LEAVE=15,ERROR=16,CHAT=17,RACE=18,DEPART=19,SAVE=20,FINISH=21;
  static final byte[] EMPTY=new byte[0],LEAVE_INPUT={2,0,87,1};
  static final class Message {
    final int type,epoch,number;final long hash;final byte[] data;
    Message(int t,int e,int n,long h,byte[] b){type=t;epoch=e;number=n;hash=h;data=b;}
  }
  static byte[] pack(int t,int epoch,int number,long hash,byte[] b)throws IOException {
    if(t<HELLO||t>FINISH||epoch<0||b==null||b.length>MAX)throw new IOException("Invalid game message");
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
    out.writeInt(0x4f425752);out.writeByte(1);out.writeByte(t);out.writeInt(epoch);out.writeInt(number);out.writeLong(hash);out.writeInt(b.length);out.write(b);return bytes.toByteArray();
  }
  static Message read(byte[] b)throws IOException {
    if(b==null||b.length<HEADER||b.length>MAX+HEADER)throw new IOException("Invalid game packet size");
    DataInputStream in=new DataInputStream(new ByteArrayInputStream(b));
    if(in.readInt()!=0x4f425752||in.readUnsignedByte()!=1)throw new IOException("All devices need the same app version");
    int t=in.readUnsignedByte(),e=in.readInt(),n=in.readInt();long h=in.readLong();int size=in.readInt();
    if(t<HELLO||t>FINISH||e<0||size!=in.available())throw new IOException("Invalid game packet");
    byte[] data=new byte[size];in.readFully(data);return new Message(t,e,n,h,data);
  }
  static String chatLine(String s){if(s==null)return "";s=s.replaceAll("[\\p{Cntrl}]"," ").trim();return s.substring(0,Math.min(246,s.length()));}
  static String chat(String s){if(s==null)return "";s=s.replaceAll("[\\p{Cntrl}]"," ").trim();return s.substring(0,Math.min(180,s.length()));}
}
