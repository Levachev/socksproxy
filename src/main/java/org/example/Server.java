package org.example;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Record;
import org.xbill.DNS.ResolverConfig;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Server {
    private Selector selector;
    private ServerSocketChannel serverSocket;
    private InetSocketAddress address;
    private ByteBuffer buffer;
    private ArrayList<User> members;
    private ArrayList<Need> needDomain = new ArrayList<>();
    InetSocketAddress resolverAddr = ResolverConfig.getCurrentConfig().server();

    Server() throws IOException {
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        address = new InetSocketAddress("localhost", 1080);
        serverSocket.bind(address);
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        buffer = ByteBuffer.allocate(1500);
        members= new ArrayList<>();
    }

    public void run() throws IOException {
        System.out.println("start");
        while (true) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();

                if (key.isValid() && key.isAcceptable()) {
                    System.out.println("get accept key");
                    handleAccept(key);
                }

                if (key.isValid() && key.isReadable()) {
                    System.out.println("get read key");
                    handleRead(key);
                }

                if (key.isValid() && key.isWritable()) {
                    //System.out.println("get write key");
                    handleWrite(key);
                }

                if (key.isValid() && key.isConnectable()) {
                    System.out.println("get connect key");
                    handleConnect(key);
                }
                iter.remove();
            }
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();

        User currentUser=getUser(client);
        if(currentUser == null){
            return;
        }

        if(!currentUser.isClient){
            if(client.finishConnect()){
                System.out.println("success connect");
                client.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ, key.attachment());
                currentUser.isConnected = true;

                User clientUser = getUser(currentUser.getDest());
                if(clientUser != null){
                    send2Reply((byte) 0, clientUser.getSocket().socket().getInetAddress(), clientUser.getSocket().socket().getPort(), clientUser.getSocket());
                    System.out.println("send succes answ 2");
                    clientUser.incrStatus();
                }
            }
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        User user = getUser(channel);
        if(user == null){
            return;
        }

        User destUser = getUser(user.getDest());
        if (destUser == null) {
            return;
        }

        for (ByteBuffer buffer : new ArrayList<ByteBuffer>(destUser.msg)) {
            try{

                int retVal = channel.write(buffer);
            } catch(IOException e){
                channel.close();
                if(user.getDest()!=null){
                    user.getDest().close();
                }
                //key.cancel();
                return;
            }
            if(!buffer.hasRemaining()){
                System.out.println("something write");
                destUser.msg.remove(buffer);
            } else{
                return;
            }
        }
        
    }

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel clientSocket = serverSocket.accept();
        clientSocket.configureBlocking(false);
        clientSocket.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, new MessageState());
        User tmp = new User(clientSocket);
        members.add(tmp);
        System.out.println("handled client");
    }

    private void handleNonClient(SocketChannel dest, SocketChannel clientSocket, InetAddress address, int port) throws IOException{
        if(!dest.isOpen()){
            return;
        }
        dest.configureBlocking(false);
        if(dest.isConnected() || dest.isConnectionPending()){
            return;
        }

        boolean retVal = dest.connect(new InetSocketAddress(address, port));
        
        if(retVal){
            dest.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, new MessageState());
        } else{
            System.out.println("not coonect");
            dest.register(selector, SelectionKey.OP_CONNECT, new MessageState());
        }

        User tmp = new User(dest);
        tmp.isClient = false;
        tmp.initDest(clientSocket);
        members.add(tmp);
        System.out.println("handled non client");
    }

    private void handleRead(SelectionKey key) throws IOException{
        
        if(key.channel() instanceof SocketChannel){
            SocketChannel client = (SocketChannel) key.channel();

            User currentUser=getUser(client);
            if(currentUser == null){
                return;
            }

            try {
                recv(key, client);
            } catch (IOException e){
                currentUser.getSocket().close();
                currentUser.getDest().close();
                e.printStackTrace();
                //key.cancel();
                //System.out.println("cancel");
                removeUser(currentUser);
                return;
            }
        } else{
            DatagramChannel client = (DatagramChannel) key.channel();
            MessageState state = (MessageState) key.attachment();
            try{
                state.buffer.flip();
                state.buffer.clear();
                int readRetVal=client.read(state.buffer);
                if(readRetVal==-1) {
                    System.out.println("DatagramChannel retVal -1");
                    client.close();
                    return;
                }
            } catch(SocketException e){
                System.out.println("DatagramChannel excp -1");
                client.close();
                return;
            }

            state.buffer.flip();
            

            if(state.isResolvedMsg){
                Need need = null;
                for(Need tmp : needDomain){
                    if(tmp.domain.equals(state.domainName)){
                        need = tmp;
                        break;
                    }
                }
                if(need == null){
                    return;
                }

                var dstAddress = getIPByMessage(state.buffer);
                if(dstAddress == null){
                    System.out.println("cannot resolve domain name "+ state.domainName);
                    return;
                }

                System.out.println("domain "+state.domainName+" "+dstAddress);
                System.out.println("vk "+InetAddress.getByName(state.domainName));

                handleNonClient(need.user.getDest(), need.user.getSocket(), dstAddress, need.user.dstPort);
                needDomain.remove(need);
            }
        }
    }
    
    private InetAddress getIPByMessage(ByteBuffer buffer) throws IOException{
        Message response = new Message(buffer);
        if(response.getRcode() == Rcode.NOERROR) {
            var records = response.getSection(Section.ANSWER);
            for(var r: records) {
                if(r instanceof ARecord aRecord) {
                    InetAddress address = aRecord.getAddress();
                    System.out.println("domain "+address);
                    return address;
                }
            }
        }
        return null;
    }

    private void removeUser(User user) throws IOException {
        user.getSocket().close();
        members.remove(user);
    }

    private void send2Reply(byte code, InetAddress address, int port, SocketChannel channel){
        byte[] addressBytes = address.getAddress();
        ByteBuffer buffer = ByteBuffer.allocate(6 + addressBytes.length);
        buffer.put((byte) 5)
                .put(code)
                .put((byte) 0)
                .put((byte) 1)
                .put(addressBytes)
                .putShort((short) port);
        buffer.flip();
        send(buffer.array(), channel);
    }

    private void send1Reply(byte method, SocketChannel channel){
        ByteBuffer buffer =  ByteBuffer.allocate(2);
        buffer.put((byte) 5)
                .put(method);
        buffer.flip();
        send(buffer.array(), channel);
    }

    private void send(byte[] byteMessage, SocketChannel channel) {
        ByteBuffer buffer = ByteBuffer.wrap(byteMessage);
        buffer.rewind();
        try {
            channel.write(buffer);
        } catch (IOException e){
            try {
                User tmp = getUser(channel);
                if(tmp != null){
                    if(tmp.getDest()!=null){
                        tmp.getDest().close();
                    }
                }
                channel.close();
            } catch (IOException e1) {
            }
            System.out.println("exception in 1 send ");
            e.printStackTrace();
        }

    }

    private void recv(SelectionKey key, SocketChannel client) throws IOException {
        User user = getUser(client);
        if(user == null){
            //key.cancel();
            client.close();
            //System.out.println("cancel 1");
            return;
        }

        MessageState state = (MessageState) key.attachment();
        try{
            state.buffer.rewind();
            state.buffer.clear();

            int readRetVal=client.read(state.buffer);

            state.buffer.flip();

            System.out.println("read len "+state.buffer.limit()+" "+readRetVal);
            if(readRetVal==-1) {
                client.close();
                if(user.getDest() != null){
                    user.getDest().close();
                }
                removeUser(user);
                return;
            }
        }
        catch(SocketException e){
            client.close();
            if(user.getDest() != null){
                user.getDest().close();
            }
            return;
        }
        
        
        int userState = user.getStatus();
        
        if(user.isClient && userState == 0){

            int ver = state.getValid1MsgVers();
            if(ver != 5){
                System.out.println("ver "+ver);
                send1Reply((byte) 255, client);
                removeUser(user);
            }
            user.incrStatus();
            System.out.println("get 1");
            send1Reply((byte) 0, client);
            System.out.println("send answ 1");
            state.buffer.clear();
            
        } else if(user.isClient && userState == 1){

            try{
                boolean ret = state.isValid2Msg();
                System.out.println(ret);
                if(ret){

                    RequestMsg requestMsg = state.getValid2Msg();
                    state.buffer.clear();
                    System.out.println("get 2");
                    if(requestMsg.getVersion() != 5) {
                        send2Reply((byte) 1, client.socket().getInetAddress(), client.socket().getPort(), client);
                        System.out.println("send bad 1 answ 2");
                    }
                    if (requestMsg.getCommand() != 1) {
                        send2Reply((byte) 7, client.socket().getInetAddress(), client.socket().getPort(), client);
                        System.out.println("send bad 2 answ 2");
                    }
                    if(requestMsg.getAddressType() != 1 && requestMsg.getAddressType() != 3) {
                        send2Reply((byte) 8, client.socket().getInetAddress(), client.socket().getPort(), client);
                        System.out.println("send bad 3 answ 2");
                    }
                    int dstPort = requestMsg.getDstPort();
                    switch (requestMsg.getAddressType()) {
                        case 1 -> {
                            System.out.println("ipv4");
                            InetAddress address = requestMsg.getDstAddress();

                            SocketChannel dest = SocketChannel.open();
                            //dest.connect(new InetSocketAddress(address, dstPort));
                            user.initDest(dest);
                            user.dstPort = dstPort;
                            handleNonClient(dest, user.getSocket(), address, dstPort);
                        }
                        case 3 -> {
                            String domain = requestMsg.getDstDomainName();
                            //DnsResolver.getInstance().resolve(domain, this);
                            SocketChannel dest = SocketChannel.open();
                            user.initDest(dest);
                            user.dstPort = dstPort;
                            System.out.println("in case 3 port "+ dstPort);

                            needDomain.add(new Need(user, domain));
                            askDomain(domain);

                            //handleNonClient(dest, user.getSocket(), InetAddress.getByName(domain), dstPort);
                        }
                        
                    }
                    // send2Reply((byte) 0, client.socket().getInetAddress(), client.socket().getPort(), client);
                    // System.out.println("send succes answ 2");
                    // user.incrStatus();
                }
            } catch(RuntimeException e){
                System.out.println("remove");
                e.printStackTrace();
                user.getSocket().close();
                if(user.getDest() != null){
                    user.getDest().close();
                }
                removeUser(user);
            }

        } else{
            System.out.println("get default is clinet "+user.isClient);
            ByteBuffer tmp = copyFrom(state.buffer);
            //tmp.flip();
            //System.out.println("read len "+tmp.limit());
            user.msg.add(tmp);
            //user.getDest().register(selector, SelectionKey.OP_READ, new MessageState());
        }
    }

    private User getUser(SocketChannel channel){
        if(channel == null){
            return null;
        }
        for (User member : members) {
            if(member.getSocket().equals(channel)){
                return member;
            }
        }
        return null;
    }

    private void askDomain(String domain) throws IOException{
        DatagramChannel socket = DatagramChannel.open();
        socket.bind(null);
        socket.configureBlocking(false);
        socket.connect(resolverAddr);

        MessageState msgState = new MessageState();
        msgState.isResolvedMsg = true;
        msgState.domainName = domain;

        socket.register(selector, SelectionKey.OP_READ, msgState);

        Record queryRecord = Record.newRecord(Name.fromString(domain + '.'), Type.A, DClass.IN);
        Message queryMessage = Message.newQuery(queryRecord);
        byte[] bytes = queryMessage.toWire(1024);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        socket.send(buffer, socket.getRemoteAddress());
    }

    public static ByteBuffer copyFrom(ByteBuffer in) {
        ByteBuffer out = ByteBuffer.allocate(in.limit());
        out.put(in);
        out.flip();
        return out;
    }


}