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


import GsonClasses.Commands.*;
import GsonClasses.Responses.*;
import OrderBook.*;
import Eseguibili.Main.ServerMain;
import Varie.*;

public class Worker implements Runnable {

    private Socket receivedSocket;
    public ConcurrentHashMap <String, Tupla> userMap;
    public ConcurrentSkipListMap <String, SocketUDPValue> socketMapUDP;
    public OrderBook orderBook;
    public TimeoutHandler handlerTimeout;
    public long maxDelay;

    public int UDPport;

    private String username = null;
    private String password = null;
    private String onlineUser = null;

    private static Gson gson = new Gson();
    public GsonResponse response = new GsonResponse();
    public GsonResponseOrder responseOrder = new GsonResponseOrder();

    public static AtomicBoolean running = new AtomicBoolean(true); //usata per interrompere l'esecuzione del Worker
    
    public Worker(Socket receivedSocket, ConcurrentHashMap <String, Tupla> userMap, OrderBook orderBook, int UDPport, ConcurrentSkipListMap <String, SocketUDPValue> socketMapUDP, long maxDelay){
        this.receivedSocket = receivedSocket;
        this.userMap = userMap;
        this.orderBook = orderBook;
        this.UDPport = UDPport;
        this.socketMapUDP = socketMapUDP;
        this.maxDelay = maxDelay;
    }

    public class SharedState{
        public AtomicBoolean activeUser = new AtomicBoolean(true);      //dice se un utente è attivo o meno
        public AtomicBoolean runningHandler = new AtomicBoolean(true);  //usata per interrompere l'esecuzione dell'handlerTimeout
        public volatile long lastActivity = System.currentTimeMillis();
    }

    @Override
    public void run() {
        System.out.printf(Ansi.BLUE + "[--WORKER %s--] " + Ansi.RESET + "serving a client\n", Thread.currentThread().getName());

        SharedState state = new SharedState();

        //thread di gestione timeout inattività client
        handlerTimeout = new TimeoutHandler(state, maxDelay, Thread.currentThread());
        Thread timeout = new Thread(handlerTimeout);
        timeout.start();

        try(DatagramSocket UDPsocket = new DatagramSocket(UDPport)){
            receivedSocket.setSoTimeout(5000);

            try(BufferedReader reader = new BufferedReader(new InputStreamReader(receivedSocket.getInputStream())); PrintWriter writer = new PrintWriter(receivedSocket.getOutputStream(), true)){

                response.setResponse("UDPport", UDPport, "OK");
                response.sendMessage(gson, writer);

                while(state.activeUser.get() && running.get()){
                    try{
                        String message = reader.readLine();

                        handlerTimeout.setTimeStamp(System.currentTimeMillis());

                        //System.out.printf(Ansi.BLUE + "[--WORKER %s--] " + Ansi.RESET + "Received message: %s\n", Thread.currentThread().getName(), message);

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

                                    response.setResponse("register",100, "OK");
                                    response.sendMessage(gson,writer);
                                }
                                else{
                                    response.setResponse("register",102, "username not available");
                                    response.sendMessage(gson,writer);
                                }
                            break;
                            case "login":
                                objValues = obj.getAsJsonObject("values");
                                GsonUser valuesL = new Gson().fromJson(objValues, GsonUser.class);

                                username = valuesL.getUsername();
                                password = valuesL.getPassword();

                                try{
                                    if(userMap.containsKey(username) == false || ((userMap.get(username)).getPassword().equals(password)) == false){
                                        response.setResponse("login",101, "username/password mismatch or non existent username");
                                        response.sendMessage(gson,writer);
                                    }
                                    else if((userMap.get(username)).getIsLogged()){
                                        response.setResponse("login",102, "user already logged");
                                        response.sendMessage(gson,writer);
                                    }
                                    else if(onlineUser != null){
                                        response.setResponse("login",103, "other error cases");
                                        response.sendMessage(gson, writer);
                                    }
                                    else{
                                        onlineUser = username;
                                        userMap.replace(username, new Tupla(password, true));
                                        updateUserMap(userMap);

                                        handlerTimeout.setUser(onlineUser);

                                        response.setResponse("login",100,"OK");
                                        response.sendMessage(gson,writer);

                                        byte buf[] = new byte[1];
                                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                                        UDPsocket.receive(packet); //ricezione del pacchetto di conferma dal client
                                        //System.out.println("[--WORKER " + Thread.currentThread().getName() + "--] Received UDP packet from client");

                                        SocketUDPValue socketUDPValue = new SocketUDPValue(packet.getAddress(), packet.getPort());

                                        if(socketMapUDP.containsKey(onlineUser)){
                                            socketMapUDP.replace(onlineUser, socketUDPValue);
                                        }
                                        else{
                                            socketMapUDP.put(onlineUser, socketUDPValue);
                                        }
                                        System.out.printf(Ansi.GREEN + "[--WORKER %s--] " + Ansi.RESET + "User %s logged in\n", Thread.currentThread().getName(), onlineUser);
                                    }
                                }
                                catch(Exception e){
                                    response.setResponse("login",103, e.getMessage());
                                    response.sendMessage(gson,writer);
                                }
                            break;

                            case "updateCredentials":
                                objValues = obj.getAsJsonObject("values");
                                GsonUpdateCredentials valuesUC = new Gson().fromJson(objValues, GsonUpdateCredentials.class);

                                username = valuesUC.getUsername();
                                String old_password = valuesUC.getOldPassword();
                                String new_password = valuesUC.getNewPassword();

                                //System.out.printf(Ansi.YELLOW + "[--WORKER %s--] " + Ansi.RESET + "Updating credentials for user %s\n", Thread.currentThread().getName(), username);

                                try{
                                    if(onlineUser != null){
                                        response.setResponse("updateCredentials",104, "user currently logged in");
                                        response.sendMessage(gson,writer);
                                    }
                                    else if(userMap.containsKey(username) == false || (userMap.get(username)).getPassword().equals(old_password) == false){
                                        response.setResponse("updateCredentials",102, "username/old_password mismatch or non existent username");
                                        response.sendMessage(gson,writer);
                                    }
                                    else if(old_password.equals(new_password) == true){
                                            response.setResponse("updateCredentials",103, "new password equals to old one");
                                            response.sendMessage(gson,writer);
                                    }
                                    else if(validateString(new_password) == false){
                                        response.setResponse("updateCredentials",101, "invalid new password");
                                        response.sendMessage(gson,writer);
                                    }
                                    else{
                                        userMap.replace(username, new Tupla(new_password, false));
                                        updateUserMap(userMap);
                                        
                                        response.setResponse("updateCredentials",100, "OK");
                                        response.sendMessage(gson,writer);
                                    }
                                    
                                }
                                catch (Exception e){
                                    response.setResponse("updateCredentials",105, "other error cases");
                                    response.sendMessage(gson,writer);
                                }
                            break;

                            case "logout":
                                try{
                                    if(onlineUser == null){
                                        response.setResponse("logout",101, "user not logged in or other error cases");
                                        response.sendMessage(gson,writer);
                                    }   
                                    else{
                                        System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Logging out user %s\n", Thread.currentThread().getName(), onlineUser);
                                    
                                        userMap.replace(onlineUser, new Tupla(password, false));
                                        updateUserMap(userMap);
                                    
                                        onlineUser = null;

                                        response.setResponse("logout",100, "OK");
                                        response.sendMessage(gson,writer);
                                    }
                                    state.activeUser.set(false);
                                    
                                    state.runningHandler.set(false);
                                    timeout.join();

                                    ServerMain.workerList.remove(this);
                                    receivedSocket.close();
                                    return;
                                }
                                catch(Exception e){
                                    response.setResponse("logout",101, "user not logged in or other error cases");
                                    response.sendMessage(gson,writer);
                                }
                            break;

                            case "insertLimitOrder":
                                try{
                                    objValues = obj.getAsJsonObject("values");
                                    GsonLimitStopOrder valuesLimit = new Gson().fromJson(objValues, GsonLimitStopOrder.class);

                                    String type = valuesLimit.getType();
                                    int size = valuesLimit.getSize();
                                    int price = valuesLimit.getPrice();
                                    int orderId = -1;

                                    //non c'è bisogno di controllare il limite superiore di size e price in quanto sono di tipo int quindi già limitati a (2^31)-1
                                    if(size <= 0 || price <= 0){
                                        responseOrder.setResponseOrder("-1");
                                        responseOrder.sendMessage(gson, writer);
                                        break;
                                    }

                                    if(type.equals("ask")){
                                        //inserimento ordine di vendita
                                        orderId = orderBook.askOrder(size, price, onlineUser, socketMapUDP);
                                    }
                                    else if(type.equals("bid")){
                                        //inserimento ordine di acquisto
                                        orderId = orderBook.bidOrder(size, price, onlineUser, socketMapUDP);
                                    }
                                    else{
                                        responseOrder.setResponseOrder("-1");
                                        responseOrder.sendMessage(gson, writer);
                                        break;
                                    }

                                    orderBook.checkStopOrders(socketMapUDP);

                                    //aggiornamento orderBook.json

                                    responseOrder.setResponseOrder(String.valueOf(orderId));
                                    responseOrder.sendMessage(gson, writer);
                                }
                                catch(Exception e){
                                    System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Error in limitOrder: %s\n", Thread.currentThread().getName(), e.getMessage());
                                    responseOrder.setResponseOrder("-1");
                                    responseOrder.sendMessage(gson, writer);
                                }
                            break;

                            case "insertMarketOrder":
                                try{
                                    objValues = obj.getAsJsonObject("values");
                                    GsonMarketOrder valuesMarket = new Gson().fromJson(objValues, GsonMarketOrder.class);

                                    String type = valuesMarket.getType();
                                    int size = valuesMarket.getSize();
                                    int orderId = -1;

                                    //non c'è bisogno di controllare il limite superiore di size in quanto è di tipo int quindi già limitato a (2^31)-1
                                    if(size <= 0){
                                        responseOrder.setResponseOrder("-1");
                                        responseOrder.sendMessage(gson, writer);
                                        break;
                                    }
                                    orderId = orderBook.marketOrder(type, size, onlineUser, socketMapUDP);

                                    orderBook.updateOrderBook();

                                    orderBook.checkStopOrders(socketMapUDP);

                                    responseOrder.setResponseOrder(String.valueOf(orderId));
                                    responseOrder.sendMessage(gson, writer);
                                }
                                catch(Exception e){
                                    System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Error in marketOrder: %s\n", Thread.currentThread().getName(), e.getMessage());
                                    responseOrder.setResponseOrder("-1");
                                    responseOrder.sendMessage(gson, writer);
                                }
                                
                            break;

                            case "insertStopOrder":
                                objValues = obj.getAsJsonObject("values");
                                GsonLimitStopOrder valuesStop = new Gson().fromJson(objValues, GsonLimitStopOrder.class);

                                String type = valuesStop.getType();
                                int size = valuesStop.getSize();
                                int price = valuesStop.getPrice();

                                //non c'è bisogno di controllare il limite superiore di size e price in quanto sono di tipo int quindi già limitati a (2^31)-1
                                if(size <= 0 || price <= 0){
                                    responseOrder.setResponseOrder("-1");
                                    responseOrder.sendMessage(gson, writer);
                                    break;
                                }
                                if(!type.equals("ask") && !type.equals("bid")){
                                    responseOrder.setResponseOrder("-1");
                                    responseOrder.sendMessage(gson, writer);
                                    break;
                                }
                                int orderId = orderBook.getLastOrderID();
                                orderBook.stopQueue.add(new StopValue(orderId, onlineUser, type, size, price));

                                orderBook.checkStopOrders(socketMapUDP);

                                responseOrder.setResponseOrder(String.valueOf(orderId));
                                responseOrder.sendMessage(gson, writer);

                            break;

                            case "cancelOrder":
                                try{
                                    objValues = obj.getAsJsonObject("values");
                                    GsonCancelOrder valuesO = new Gson().fromJson(objValues, GsonCancelOrder.class);
                                    int res = orderBook.cancelOrder(valuesO.getOrderId(), onlineUser);
                                    if(res == 100){
                                        response.setResponse("cancelOrder", res, "OK");
                                        response.sendMessage(gson, writer);
                                    }
                                    else{
                                        response.setResponse("cancelOrder", res, "order does not exist or belongs to different user or has already been finalized or other cases");
                                        response.sendMessage(gson, writer);
                                    }
                                }
                                catch(Exception e){
                                    System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Error in cancelOrder\n", Thread.currentThread().getName());
                                }
                            break;

                            case "showOrderBook":
                                try{
                                    if(onlineUser == null){
                                        response.setResponse("showOrderBook", 101, "user not logged in");
                                        response.sendMessage(gson, writer);
                                        break;
                                    }
                                    String orderBookString = orderBook.showOrderBook();

                                    response.setResponse("showOrderBook", 100, orderBookString);
                                    response.sendMessage(gson, writer);
                                }
                                catch(Exception e){
                                    System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Error in showOrderBook\n", Thread.currentThread().getName());
                                }
                            break;

                            case "getPriceHistory":
                                try{
                                    objValues = obj.getAsJsonObject("values");

                                    GsonPriceHistory valuesPH = new Gson().fromJson(objValues, GsonPriceHistory.class);
                                    String date = valuesPH.getMonth();
                                    //formato date: MMYYYY
                                    int month = Integer.parseInt(date.substring(0, 2));
                                    int year = Integer.parseInt(date.substring(2, 6));
                                    System.out.printf(Ansi.YELLOW + "[--WORKER %s--] " + Ansi.RESET + "Getting price history for month %d and year %d\n", Thread.currentThread().getName(), month, year);

                                    if(month < 1 || month > 12 && year < 1970){
                                        response.setResponse("getPriceHistory", 101, "invalid month or year");
                                        response.sendMessage(gson, writer);
                                        break;
                                    }

                                    String priceHistory = orderBook.getPriceHistory(String.valueOf(month), String.valueOf(year));

                                    response.setResponse("getPriceHistory", 100, priceHistory);
                                    response.sendMessage(gson, writer);
                                }
                                catch(Exception e){
                                    System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Error in getPriceHistory\n", Thread.currentThread().getName());
                                }
                            break;

                            default:
                                System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Error, command received not found\n", Thread.currentThread().getName());
                        }
                    }
                    catch(SocketTimeoutException e){
                        if(state.activeUser.get() == false){
                            if(onlineUser != null){
                                System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Logging out user %s due to inactivity\n", Thread.currentThread().getName(), onlineUser);
                            }
                            else{
                                System.out.printf(Ansi.BLUE + "[--WORKER %s--] " + Ansi.RESET + "Client inactive, closing connection\n", Thread.currentThread().getName());
                            }
                            break;
                        }
                        else continue;
                    }
                }
                //chiusura connessione e terminazione worker
                state.runningHandler.set(false);
                timeout.join();

                if(state.activeUser.get() == false){
                    response.setResponse("disconnection", 100, "Closing connection due to inactivity");
                }
                if(running.get() == false){
                    response.setResponse("disconnection", 100, "Closing connection due to server shutdown");
                }

                if(onlineUser != null){
                    userMap.replace(onlineUser, new Tupla(password, false));
                    updateUserMap(userMap);
                }
                response.sendMessage(gson,writer);

                ServerMain.workerList.remove(this);
                receivedSocket.close();

                return;
            }
            catch(Exception e) {
                System.out.printf(Ansi.RED + "[--WORKER %s--] " + Ansi.RESET + "Error in worker: %s\n", Thread.currentThread().getName(), e.getMessage());
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

    public static void updateOrderBook(OrderBook orderBook){
        try(BufferedWriter writer = new BufferedWriter(new FileWriter("src/main/java/JsonFile/orderBook.json"))){
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            writer.write(g.toJson(orderBook));

        } catch (Exception e){
            System.err.printf("[WORKER] updateJsonOrderBook %s \n",e.getMessage());
        }
    }

    public void shutdown(){
        running.set(false);
    }
}