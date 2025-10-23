package Eseguibili.Client;

import java.io.BufferedReader;
import java.net.DatagramSocket;

import com.google.gson.Gson;

import Varie.Ansi;



public class ReceiverUDP implements Runnable{
    DatagramSocket socket;
    BufferedReader reader;
    Printer printer;

    public ReceiverUDP(DatagramSocket socket, BufferedReader reader, Printer printer){
        this.socket = socket;
        this.reader = reader;
        this.printer = printer;
    }

    public void run(){
        while(true){
            try{
                //ricezione messaggi UDP dal server
                byte[] buffer = new byte[1024];
                java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);

                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());

                Gson gson = new Gson();

            }
            catch(Exception e){
                System.err.println("[Client-ReceiverUDP] " + Ansi.RED + "Error in UDP receiver: " + e.getMessage() + Ansi.RESET);
            }
        }
    }
}
