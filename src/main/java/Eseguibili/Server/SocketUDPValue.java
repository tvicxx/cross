package Eseguibili.Server;

import java.net.InetAddress;

//classe SocketUDPValue per memorizzare l'indirizzo e la porta di un client UDP
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