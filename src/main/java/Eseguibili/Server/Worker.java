package Eseguibili.Server;

import java.io.*;
import java.net.*;
//import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

//import GsonClasses.*;
import GsonClasses.Commands.*;
import GsonClasses.Responses.*;
import Eseguibili.Main.ServerMain;
import Varie.*;

public class Worker implements Runnable {

    private Socket receivedSocket;
    public ConcurrentHashMap <String, Tupla> userMap;

    public int UDPport;

    private String username = null;
    private String password = null;
    private String onlineUser = null;

    private static Gson gson = new Gson();
    public GsonResponse response = new GsonResponse();


    public class SharedState{
        public AtomicBoolean activeUser = new AtomicBoolean(true);      // Dice se un utente è attivo o meno
        public AtomicBoolean runningHandler = new AtomicBoolean(true);  // Usata per interrompere l'esecuzione dell'Handler
        public volatile long lastActivity = System.currentTimeMillis();
    }
    
    public Worker(Socket receivedSocket, ConcurrentHashMap <String, Tupla> userMap){
        this.receivedSocket = receivedSocket;
        this.userMap = userMap;
    }

    @Override
    public void run() {
        System.out.printf(Ansi.BLUE + "[--WORKER %s--] " + Ansi.RESET + "serving a client\n", Thread.currentThread().getName());

        SharedState state = new SharedState();

        try(DatagramSocket UDPsocket = new DatagramSocket(UDPport)){
            receivedSocket.setSoTimeout(5000);

            try(BufferedReader reader = new BufferedReader(new InputStreamReader(receivedSocket.getInputStream())); PrintWriter writer = new PrintWriter(receivedSocket.getOutputStream(), true)){

                while(state.activeUser.get()){
                    try{
                        String message = reader.readLine();

                        JsonObject obj = JsonParser.parseString(message).getAsJsonObject();
                        String operation = obj.get("operation").getAsString();
                        JsonObject objValues;

                        switch(operation){
                            case "register":
                                objValues = obj.getAsJsonObject("values");
                                GsonUser valuesR = new Gson().fromJson(objValues, GsonUser.class);

                                username = valuesR.getUsername();
                                password = valuesR.getPassword();

                                System.out.printf(Ansi.GREEN + "[--WORKER %s--] " + Ansi.RESET + "Registering user %s with password %s\n", Thread.currentThread().getName(), username, password);

                                if(onlineUser != null){
                                    response.setResponse("register", 103, "other error cases");
                                    response.sendMessage(gson,writer);
                                }
                                else if(validateString(password) == false){
                                    response.setResponse("register", 101, "invalid password");
                                    response.sendMessage(gson,writer);
                                }
                                else if((userMap.putIfAbsent(username, new Tupla(password, false))) == null){

                                    //modifica il file usermap.json inserendo il nuovo utente
                                    updateUserMap(userMap);

                                    response.setResponse("register", 100, "OK");
                                    response.sendMessage(gson,writer);
                                }
                                else{
                                    response.setResponse("register", 102, "username not available");
                                    response.sendMessage(gson,writer);
                                }
                            break;
                            case "login":
                                objValues = obj.getAsJsonObject("values");
                                GsonUser valuesL = new Gson().fromJson(objValues, GsonUser.class);

                                username = valuesL.getUsername();
                                password = valuesL.getPassword();

                                try{
                                    if(userMap.containsKey(username) == false){
                                        response.setResponse("login", 101, "username/password mismatch or non existent username");
                                        response.sendMessage(gson,writer);
                                    }
                                    else if(((userMap.get(username)).getPassword().equals(password)) == false){
                                        response.setResponse("login", 101, "username/password mismatch or non existent username");
                                        response.sendMessage(gson,writer);
                                    }
                                    else if((userMap.get(username)).getIsLogged()){
                                        response.setResponse("login", 102, "user already logged");
                                        response.sendMessage(gson,writer);
                                    }
                                    else if(onlineUser != null){
                                        response.setResponse("login", 103, "other error cases");
                                        response.sendMessage(gson, writer);
                                    }
                                    else{
                                        onlineUser = username;
                                        userMap.replace(username, new Tupla(password, true));
                                        updateUserMap(userMap);

                                        response.setResponse("login",100,"OK");
                                        response.sendMessage(gson,writer);
                                        System.out.printf(Ansi.GREEN + "[--WORKER %s--] " + Ansi.RESET + "User %s logged in\n", Thread.currentThread().getName(), onlineUser);
                                    }
                                }
                                catch(Exception e){
                                    response.setResponse("login", 103, e.getMessage());
                                    response.sendMessage(gson,writer);
                                }
                            break;

                            case "updateCredentials":
                                objValues = obj.getAsJsonObject("values");
                                GsonUpdateCredentials valuesUC = new Gson().fromJson(objValues, GsonUpdateCredentials.class);

                                username = valuesUC.getUsername();
                                String old_password = valuesUC.getOldPassword();
                                String new_password = valuesUC.getNewPassword();

                                System.out.printf(Ansi.YELLOW + "[--WORKER %s--] " + Ansi.RESET + "Updating credentials for user %s\n", Thread.currentThread().getName(), username);

                                try{
                                    if(onlineUser != null){
                                        response.setResponse("updateCredentials", 104, "user currently logged in");
                                        response.sendMessage(gson,writer);
                                    }
                                    else if(userMap.containsKey(username) == false || (userMap.get(username)).getPassword().equals(old_password) == false){
                                        response.setResponse("updateCredentials", 102, "username/old_password mismatch or non existent username");
                                        response.sendMessage(gson,writer);
                                    }
                                    else if(old_password.equals(new_password) == true){
                                            response.setResponse("updateCredentials", 103, "new password equals to old one");
                                            response.sendMessage(gson,writer);
                                    }
                                    else if(validateString(new_password) == false){
                                        response.setResponse("updateCredentials", 101, "invalid new password");
                                        response.sendMessage(gson,writer);
                                    }
                                    else{
                                        userMap.replace(username, new Tupla(new_password, false));
                                        updateUserMap(userMap);
                                        
                                        response.setResponse("updateCredentials", 100, "OK");
                                        response.sendMessage(gson,writer);
                                    }
                                    
                                }
                                catch (Exception e){
                                    response.setResponse("updateCredentials", 105, "other error cases");
                                    response.sendMessage(gson,writer);
                                }
                            break;

                            case "logout":
                                if(onlineUser == null){
                                    response.setResponse("logout", 101, "user not logged in or other error cases");
                                    response.sendMessage(gson,writer);
                                }
                                else{
                                    System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Logging out user %s\n", Thread.currentThread().getName(), onlineUser);
                                    
                                    userMap.replace(onlineUser, new Tupla(password, false));
                                    updateUserMap(userMap);
                                    
                                    onlineUser = null;

                                    response.setResponse("logout", 100, "OK");
                                    response.sendMessage(gson,writer);
                                    state.activeUser.set(false);

                                    ServerMain.workerList.remove(this);

                                    receivedSocket.close();
                                    return;
                                }
                            break;
                        }
                    }
                    catch(Exception e){
                        System.err.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Error processing client request: %s\n", Thread.currentThread().getName(), e.getMessage());
                    }
                }
            }
        }
        catch(IOException e){
            System.err.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Error creating UDP socket: %s\n", Thread.currentThread().getName(), e.getMessage());
        }
    }

    //verifica se una stringa è valida
    public static boolean validateString(String string){
        if(string == null || string.isEmpty()) return false;
        return string.matches("^[a-zA-Z0-9]+$");
    }

    public static void updateUserMap(ConcurrentHashMap<String, Tupla> userMap){
        try(BufferedWriter writer = new BufferedWriter(new FileWriter("src/main/java/JsonFile/userMap.json"))){
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            writer.write(g.toJson(userMap));

        } catch (Exception e){
            System.err.printf("[WORKER] updateJsonUsermap %s \n",e.getMessage());
        }
    }
}
