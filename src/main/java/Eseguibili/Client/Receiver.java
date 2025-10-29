package Eseguibili.Client;

import java.io.*;
import java.net.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import Eseguibili.Main.ClientMain.SharedData;
import Varie.*;

public class Receiver implements Runnable{

    public Socket socketTCP;
    public BufferedReader reader;
    public Printer printer;
    public SharedData shared;

    public Receiver(Socket socketTCP, BufferedReader reader, Printer writer, SharedData shared){
        this.socketTCP = socketTCP;
        this.reader = reader;
        this.printer = writer;
        this.shared = shared;
    }

    public void run(){
        try{
            String message;
            while(Thread.currentThread().isInterrupted() == false && socketTCP.isClosed() == false && (message = reader.readLine()) != null){
                JsonObject jsonMess = JsonParser.parseString(message).getAsJsonObject();
                //System.out.println("[Receiver] Messaggio ricevuto: " + message);
                
                //ricezione del comando UDPport
                if(jsonMess.get("type").getAsString().equals("UDPport")){
                    shared.UDPport = jsonMess.get("response").getAsInt();
                    continue;
                }
                if(jsonMess.get("response") != null){
                    if(jsonMess.get("response").getAsInt() == 100){
                        printer.print("[Client] " + Ansi.GREEN + "Operation successful!" + Ansi.RESET);
                        if(jsonMess.get("type").getAsString().equals("login")){
                            shared.isLogged.set(true);
                            shared.loginError.set(false);
                        }
                        else if(jsonMess.get("type").getAsString().equals("logout")){
                            shared.isClosed.set(true);
                        }
                    }
                    else{
                        printer.print("[Client] " + Ansi.RED + jsonMess.get("errorMessage").getAsString() + Ansi.RESET);
                    }
                    printer.prompt();
                }
                else if(jsonMess.get("orderId") != null){
                    int orderId = jsonMess.get("orderId").getAsInt();
                    if(orderId != -1){
                        printer.print("[Client] " + Ansi.GREEN + "Order inserted successfully! Order ID: " + orderId + Ansi.RESET);
                    }
                    else{
                        printer.print("[Client] " + Ansi.RED + "Errore nell'ordine" + Ansi.RESET);
                    }
                    printer.prompt();
                }
            }
        }
        catch(Exception e){
            printer.print(Ansi.RED + "[Receiver] Error: " + e.getMessage() + Ansi.RESET);
            shared.isClosed.set(true);
        }
    }
}
