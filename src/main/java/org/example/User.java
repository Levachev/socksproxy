package org.example;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

public class User {
    private SocketChannel socket;
    private SocketChannel dest;
    private int status = 0;
    public boolean isClient = true;
    public boolean isConnected = false;
    public int dstPort = 0;
    public ArrayList<ByteBuffer> msg = new ArrayList<>();

    User(SocketChannel socket){
        this.socket=socket;
        dest = null;
    }

    public SocketChannel getSocket() {
        return this.socket;
    }

    public void setSocket(SocketChannel socket) {
        this.socket = socket;
    }

    public void initDest(SocketChannel dest){
        this.dest = dest;
    }

    public SocketChannel getDest(){
        return dest;
    }

    public int getStatus() {
        return this.status;
    }

    public void incrStatus() {
        status++;
    }


}