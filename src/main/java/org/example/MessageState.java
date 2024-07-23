package org.example;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MessageState {
    public boolean isResolvedMsg = false;
    public String domainName = "";
    public ByteBuffer buffer;

    public MessageState(){
        buffer = ByteBuffer.allocate(1500);
    }

    public boolean isValid2Msg(){
        //ByteBuffer tmp = ByteBuffer.wrap(buffer.array());
        ByteBuffer tmp = Server.copyFrom(buffer);
        //System.out.println(Arrays.toString(tmp.array()));
        try {
            byte version = tmp.get();
            System.out.println("ver in haser "+version);
            byte cmd = tmp.get();
            byte ignoredReserved = tmp.get();
            byte addrType = tmp.get();
            if(addrType == 1){
                byte[] address = new byte[4];
                tmp.get(address);
                //dstAddress = Inet4Address.getByAddress(address);
            } else if(addrType == 3){
                byte len = tmp.get();
                byte[] bytes = new byte[len];
                tmp.get(bytes);
            } else if(addrType == 4){
                byte[] address = new byte[16];
                tmp.get(address);
            } else{
                throw new RuntimeException();
            }
            short dstPort = tmp.getShort();
            return true;
        } catch (BufferUnderflowException e) {
            return false;
        }
    }

    public int getValid1MsgVers(){
        //System.out.println(Arrays.toString(buffer.array()));
        try{
            byte version = buffer.get();
            System.out.println(version);
            byte len = buffer.get();
            for(int i=0;i<len;i++){
                byte method = buffer.get();
            }
            return version;
        } catch(BufferUnderflowException e){
            return -1;
        }
    }

    public RequestMsg getValid2Msg(){
        return RequestMsg.parseFrom(buffer);
    }
}