package Eseguibili.Server;

import java.net.InetAddress;

public class SocketUDPValue {
    public int port;
    public InetAddress address;

    public SocketUDPValue(InetAddress address, int port){
        this.address = address;
        this.port = port;
    }

    public int getPort(){
        return this.port;
    }

    public InetAddress getAddress(){
        return this.address;
    }
}