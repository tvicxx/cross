package Eseguibili.Client;

import java.io.*;
import java.net.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import Eseguibili.Main.ClientMain.SharedData;
import Varie.*;

public class Receiver implements Runnable{

    private static String closingMessage =  "\n" + 
                                "-----------------------------------------------------------------------\n" + 
                                "Grazie per aver usato il nostro servizio di trading!\n" +
                                "\n"+
                                "Credits: Vicarelli Tommaso - Mat. 638912\n";

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
                //printer.print("[Client-Receiver] " + Ansi.GREEN + "Message received from server: " + message + Ansi.RESET);
                
                if(jsonMess.get("response") != null){
                    if(jsonMess.get("response").getAsInt() == 100){
                        if(jsonMess.get("type").getAsString().equals("getPriceHistory")){
                            printer.print(jsonMess.get("errorMessage").getAsString());
                            printer.prompt();
                            continue;
                        }
                        else if(jsonMess.get("type").getAsString().equals("showOrderBook")){
                            printer.print(jsonMess.get("errorMessage").getAsString());
                            printer.prompt();
                            continue;
                        }
                        else if(jsonMess.get("type").getAsString().equals("logout")){
                            printer.print(Ansi.BLUE + closingMessage + Ansi.RESET);
                            shared.isClosed.set(true);
                            System.exit(0);
                        }
                        else if(jsonMess.get("type").getAsString().equals("disconnection")){
                            printer.print(Ansi.RED_BACKGROUND + "[Client] " + jsonMess.get("errorMessage").getAsString() + Ansi.RESET);
                            printer.print(Ansi.BLUE + closingMessage + Ansi.RESET);
                            shared.isClosed.set(true);
                            System.exit(0);
                        }
                        else printer.print("[Client] " + Ansi.GREEN + "Operation successful!" + Ansi.RESET);

                        if(jsonMess.get("type").getAsString().equals("login")){
                            shared.isLogged.set(true);
                            shared.loginError.set(false);
                        }
                    }
                    else if(jsonMess.get("type").getAsString().equals("UDPport")){
                        shared.UDPport = jsonMess.get("response").getAsInt();
                        continue;
                    }
                    else{
                        printer.print("[Client] " + Ansi.RED + jsonMess.get("errorMessage").getAsString() + Ansi.RESET);

                        if(jsonMess.get("type").getAsString().equals("login")){
                            shared.loginError.set(true);
                            shared.isLogged.set(false);
                        }
                        if(jsonMess.get("type").getAsString().equals("logout")){
                            shared.isLogged.set(true);
                            return;
                        }
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