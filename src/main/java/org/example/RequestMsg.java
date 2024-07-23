package org.example;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class RequestMsg {
    private final byte version;
    private final byte command;
    private final byte addressType;
    private final Object dstAddress;
    private final int dstPort;

    private RequestMsg(byte version, byte command, byte addressType, Object dstAddress, int dstPort) {
        this.version = version;
        this.command = command;
        this.addressType = addressType;
        this.dstAddress = dstAddress;
        this.dstPort = dstPort;
    }

    public byte getVersion() {
        return version;
    }

    public byte getCommand() {
        return command;
    }

    public byte getAddressType() {
        return addressType;
    }

    public int getDstPort() {
        return dstPort;
    }

    public InetAddress getDstAddress() {
        return (InetAddress) dstAddress;
    }

    public String getDstDomainName() {
        return (String) dstAddress;
    }

    public static RequestMsg parseFrom(ByteBuffer buffer){
        buffer.rewind();
        //System.out.println(Arrays.toString(buffer.array()));
        try {
            //System.out.println("position "+buffer.position());
            byte version = buffer.get();
            byte command = buffer.get();
            byte ignoredReserved = buffer.get();
            byte type = buffer.get();
            Object dstAddress;
            switch (type) {
                case 1 -> {
                    byte[] address = new byte[4];
                    buffer.get(address);
                    dstAddress = Inet4Address.getByAddress(address);
                }
                case 3 -> {
                    byte len = buffer.get();
                    byte[] bytes = new byte[len];
                    buffer.get(bytes);
                    dstAddress = new String(bytes, StandardCharsets.US_ASCII);
                }
                case 4 -> {
                    byte[] address = new byte[16];
                    buffer.get(address);
                    dstAddress = Inet6Address.getByAddress(address);
                }
                default -> throw new RuntimeException();
            }
            short dstPort = buffer.getShort();
            int port = dstPort;
            return new RequestMsg(version, command, type, dstAddress, port);
        } catch (BufferUnderflowException | UnknownHostException e) {
            //System.out.println("tut runtime");
            throw new RuntimeException(e);
        }
    }

}
