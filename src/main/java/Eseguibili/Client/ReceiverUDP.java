package Eseguibili.Client;

import java.io.BufferedReader;
import java.net.DatagramSocket;

import com.google.gson.Gson;

import Varie.Ansi;
import OrderBook.TradeNotifyUDP;

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
                //printer.print("[Client-ReceiverUDP] " + Ansi.GREEN + "UDP packet received from " + packet.getAddress().toString() + ":" + packet.getPort() + Ansi.RESET);
                String message = new String(packet.getData(), 0, packet.getLength());

                Gson gson = new Gson();
                TradeNotifyUDP notify = gson.fromJson(message, TradeNotifyUDP.class);

                if(notify.getSize() == 0 && notify.getPrice() == 0){
                    printer.print("[Client-ReceiverUDP] " + Ansi.YELLOW + "Trade Notification: Order ID " + notify.getOrderId() + " of type " + notify.getType() + " has been fully executed." + Ansi.RESET);
                }
                else{
                    printer.print("[Client-ReceiverUDP] " + Ansi.YELLOW + "Trade Notification: Order ID " + notify.getOrderId() + " of type " + notify.getType() + " executed for size " + notify.getSize() + " at price " + notify.getPrice() + "." + Ansi.RESET);
                }
                printer.prompt();
            }
            catch(Exception e){
                System.err.println("[Client-ReceiverUDP] " + Ansi.RED + "Error in UDP receiver: " + e.getMessage() + Ansi.RESET);
            }
        }
    }
}
