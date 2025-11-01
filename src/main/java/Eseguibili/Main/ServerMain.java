package Eseguibili.Main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import Eseguibili.Server.*;
import OrderBook.*;
import Varie.*;


public class ServerMain{

    public static ServerSocket serverSocket;
    public static final String configFile = "src/main/java/server.properties";
    public static int TCPport;
    public static int UDPport;
    public static int maxDelay;
    public static String hostname;
    public static String userMapPath;
    public static String orderBookPath;


    public static ConcurrentHashMap <String, Tupla> userMap = new ConcurrentHashMap<>();
    public static ConcurrentLinkedQueue <Worker> workerList = new ConcurrentLinkedQueue<>();
    public static ConcurrentSkipListMap <String, SocketUDPValue> socketMapUDP = new ConcurrentSkipListMap<>();

    public static final ExecutorService threadPool = Executors.newCachedThreadPool();

    //strutture dati per orderBook
    public static ConcurrentSkipListMap<Integer,OrderValue> askMap = new ConcurrentSkipListMap<>();
    public static ConcurrentLinkedQueue<StopValue> stopQueue;
    public static ConcurrentSkipListMap<Integer,OrderValue> bidMap = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    public static OrderBook orderBook = new OrderBook(askMap, 0, new ConcurrentLinkedQueue<>(), bidMap, 0);

    public static void loadConfig() throws FileNotFoundException, IOException{
        InputStream input = new FileInputStream(configFile);
        Properties properties = new Properties();
        properties.load(input);
        TCPport = Integer.parseInt(properties.getProperty("TCPport"));
        UDPport = Integer.parseInt(properties.getProperty("UDPport"));
        maxDelay = Integer.parseInt(properties.getProperty("maxDelay"));
        hostname = properties.getProperty("hostname");
        userMapPath = properties.getProperty("userMapPath");
        orderBookPath = properties.getProperty("orderBookPath");
        input.close();
    }
    
    public static void main(String[] args) {
        
        try{
            System.out.println("[--ServerMain--] Loading configuration...");
            loadConfig();
        }
        catch(Exception e){
            System.err.println("[--ServerMain--] Error loading configuration file: " + e.getMessage());
            System.exit(0);
        }

        try{
            serverSocket = new ServerSocket(TCPport);

            loadUserMap();

            loadOrderBook();

            System.out.println(Ansi.YELLOW + "[--ServerMain--] Server is starting on port " + TCPport + "..." + Ansi.RESET);

            //accept e asegna al thread pool
            while (true){
                Socket receivedSocket = serverSocket.accept();
                //System.out.println(Ansi.GREEN + "[--ServerMain--] New client connected: " + receivedSocket.getInetAddress().getHostAddress()+ Ansi.RESET);
                Worker worker = new Worker(receivedSocket, userMap, orderBook, UDPport++, socketMapUDP);
                workerList.add(worker);
                threadPool.execute(worker);
            }
        }
        catch(IOException e){
            System.err.println("[--ServerMain--] " + e.getMessage());
        }

    }

    public static void loadUserMap() {
        try(JsonReader reader = new JsonReader(new FileReader(userMapPath))){

            //lettura dal file JSON userMap.json
            Gson gson = new Gson();
            Type mapType = new TypeToken<ConcurrentHashMap<String, Tupla>>(){}.getType();
            userMap = gson.fromJson(reader, mapType);
            /* 
            for (Map.Entry<String,Tupla> entry : userMap.entrySet()){
                String username = entry.getKey();
                Tupla userDetails = entry.getValue();
                String password = (String) userDetails.getPassword();
                boolean isLogged = (Boolean) userDetails.getIsLogged();

                System.out.println("Username: " + username + ", Password: " + password + ", isLogged: " + isLogged);
            }
            */
            System.out.println("[--ServerMain--] UserMap loaded successfully!");
        }
        catch(EOFException e){
            System.out.println("File utenti vuoto");
            return;
        }
        catch(Exception e){
            System.out.println("Error while loading UserMap: " + e.getMessage());
            System.exit(0);
        }
    }

    public static void loadOrderBook(){
        try(JsonReader reader = new JsonReader(new FileReader(orderBookPath))){
            Gson gson = new Gson();
            reader.beginObject();
            while(reader.hasNext()){
                String name = reader.nextName();

                //System.out.println("[--ServerMain--] Loading " + name + "...");

                if(name.equals("askMap")){
                    reader.beginObject();
                    ConcurrentSkipListMap<Integer, OrderValue> askMap = orderBook.askMap;

                    while(reader.hasNext()){
                        int price = Integer.parseInt(reader.nextName());
                        OrderValue val = gson.fromJson(reader, OrderValue.class);
                        askMap.put(price, val);
                    }
                    reader.endObject();
                }
                else if(name.equals("spread")){
                    orderBook.spread = reader.nextInt();
                    //System.out.println("[--ServerMain--] spread loaded: " + orderBook.spread);
                }
                else if(name.equals("bidMap")){
                    reader.beginObject();
                    ConcurrentSkipListMap<Integer, OrderValue> bidMap = orderBook.bidMap;
                    //System.out.println("[--ServerMain--] Reading bidMap...");

                    while(reader.hasNext()){
                        int price = Integer.parseInt(reader.nextName());
                        OrderValue val = gson.fromJson(reader, OrderValue.class);
                        bidMap.put(price, val);
                    }
                    //System.out.println("[--ServerMain--] bidMap loaded: " + bidMap.toString());
                    reader.endObject();
                }
                else if(name.equals("lastId")){
                    orderBook.lastId = reader.nextInt();
                    //System.out.println("[--ServerMain--] lastId loaded: " + orderBook.lastId);
                }
                else{
                    reader.skipValue();
                }
            }
            reader.endObject();
            System.out.println("[--ServerMain--] OrderBook loaded successfully!");
            
            //non leggo stopOrder perche' sono dati non persistenti ma lo inizializzo vuoto
            orderBook.stopQueue = new ConcurrentLinkedQueue<>();
        }
        catch(EOFException e){
            System.out.println("[--ServerMain--] File orderBook vuoto");
            return;
        }
        catch(Exception e){
            System.out.println("[--ServerMain--] Error while loading OrderBook: " + e.getMessage());
            System.exit(0);
        }
    }
}
