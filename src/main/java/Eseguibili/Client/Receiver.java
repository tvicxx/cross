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
                if(jsonMess.get("type") != null){
                    String type = jsonMess.get("type").getAsString();
                    String error = jsonMess.get("errorMessage").getAsString();
                    switch(type){
                        case "register":
                            if(error.equals("OK")){
                                printer.print("[Client] " + Ansi.GREEN + "Registration successful!" + Ansi.RESET);
                            }
                            else printer.print("[Client] " + Ansi.RED + error + Ansi.RESET);
                        break;
                        case "updateCredentials":
                            if(error.equals("OK")){
                                printer.print("[Client] " + Ansi.GREEN + "Credentials updated successfully!" + Ansi.RESET);
                            }
                            else printer.print("[Client] " + Ansi.RED + error + Ansi.RESET);
                        break;
                        case "login":
                            if(error.equals("OK")){
                                shared.isLogged.set(true);
                                shared.loginError.set(false);
                                printer.print("[Client] " + Ansi.GREEN + "Login successful!" + Ansi.RESET);
                            }
                            else {
                                shared.loginError.set(true);
                                printer.print("[Client] " + Ansi.RED + error + Ansi.RESET);
                            }
                        break;
                        case "logout":
                            if(error.equals("OK")){
                                printer.print("[Client] " + Ansi.YELLOW + "Logout successful!" + Ansi.RESET);
                            }
                            else printer.print("[Client] " + Ansi.RED + error + Ansi.RESET);
                            shared.isClosed.set(true);
                        break;
                    }
                    printer.prompt();
                }
                else if(jsonMess.get("orderId") != null){
                    int orderId = jsonMess.get("orderId").getAsInt();
                    if(orderId != -1){
                        printer.print("[Client] " + Ansi.GREEN + "Order inserted successfully! Order ID: " + orderId + Ansi.RESET);
                    }
                    else{
                        String error = jsonMess.get("errorMessage").getAsString();
                        printer.print("[Client] " + Ansi.RED + error + Ansi.RESET);
                    }
                }
            }
        }
        catch(Exception e){
            printer.print(Ansi.RED + "[Receiver] Error: " + e.getMessage() + Ansi.RESET);
            shared.isClosed.set(true);
        }
    }
}
