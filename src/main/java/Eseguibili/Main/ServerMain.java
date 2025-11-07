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
    public static long maxDelay;
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
    
    public static void main(String[] args) {
        //caricamento dati dal file di configurazione server.properties
        try{
            System.out.println("[--ServerMain--] Loading configuration...");
            loadConfig();
        }
        catch(Exception e){
            System.err.println("[--ServerMain--] Error loading configuration file: " + e.getMessage());
            System.exit(0);
        }

        try{
            //creazione server socket TCP
            serverSocket = new ServerSocket(TCPport);

            //handler gestione terminazione server con CTRL+C
            Runtime.getRuntime().addShutdownHook(new Thread(){
                public void run(){
                    System.out.println(Ansi.RED_BACKGROUND + "\n[--ServerMain--] Closing server..." + Ansi.RESET + "\n");
                    try{
                        if(serverSocket != null && !serverSocket.isClosed()){
                            serverSocket.close();
                        }
                    }
                    catch(IOException e){
                        System.err.println("[--ServerMain--] Error during closing server socket: " + e.getMessage());
                    }
                    //termina tutti i worker attivi se presenti
                    if(workerList.isEmpty() == false){
                        for(Worker worker : workerList){
                            worker.shutdown();
                        }
                    }

                    //termina il thread pool
                    threadPool.shutdown();

                    //inserisce false nell'userMap per tutti gli utenti loggati
                    for(Map.Entry<String, Tupla> entry : userMap.entrySet()){
                        Tupla userDetails = entry.getValue();
                        if((Boolean) userDetails.getIsLogged() == true){
                            userDetails.setIsLogged(false);
                        }
                    }

                    //controlla che il thread pool sia terminato, altrimenti forza la terminazione
                    try{
                        if(!threadPool.awaitTermination(5, TimeUnit.SECONDS)){
                            threadPool.shutdownNow();
                        }
                    }
                    catch(InterruptedException e){
                        threadPool.shutdownNow();
                    }

                    System.out.println(Ansi.GREEN_BACKGROUND + "[--ServerMain--] Server closed successfully!" + Ansi.RESET);

                }
            });

            //caricamento userMap e orderBook da file JSON
            loadUserMap();
            loadOrderBook();

            System.out.println(Ansi.YELLOW + "[--ServerMain--] Server is starting on port " + TCPport + "..." + Ansi.RESET);

            //accept e asegnazione al thread pool
            while(serverSocket.isClosed() == false){
                Socket receivedSocket = serverSocket.accept();
                //creazione worker per la gestione del client connesso
                Worker worker = new Worker(receivedSocket, userMap, orderBook, UDPport++, socketMapUDP, maxDelay);
                //aggiunta worker alla lista dei worker attivi
                workerList.add(worker);
                //esecuzione worker nel thread pool
                threadPool.execute(worker);
            }
        }
        catch(IOException e){
            System.err.println("[--ServerMain--] " + e.getMessage());
        }

    }

    //metodo per il caricamento del file di configurazione server.properties
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

    //metodo per il caricamento della userMap da file JSON
    public static void loadUserMap(){
        try(JsonReader reader = new JsonReader(new FileReader(userMapPath))){

            //lettura dal file JSON userMap.json
            Gson gson = new Gson();
            //definizione del tipo di struttura dati da leggere
            //sfrutto la classe Tupla appositamente creata per memorizzare password e stato di login
            Type mapType = new TypeToken<ConcurrentHashMap<String, Tupla>>(){}.getType();
            userMap = gson.fromJson(reader, mapType);
            
            /* 
            //stampa userMap caricata
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

    //metodo per il caricamento dell'orderBook da file JSON
    public static void loadOrderBook(){
        //lettura dal file JSON orderBook.json
        try(JsonReader reader = new JsonReader(new FileReader(orderBookPath))){
            Gson gson = new Gson();
            //inizio lettura orderBook
            reader.beginObject();
            while(reader.hasNext()){
                String name = reader.nextName();
                //lettura askMap
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
                //lettura spread
                else if(name.equals("spread")){
                    orderBook.spread = reader.nextInt();
                }
                //lettura bidMap
                else if(name.equals("bidMap")){
                    reader.beginObject();
                    //uso una ConcurrentSkipListMap con ordine decrescente per la bidMap, sfruttando la classe OrderValue per memorizzare le informazioni sugli ordini divisi per prezzo
                    ConcurrentSkipListMap<Integer, OrderValue> bidMap = orderBook.bidMap;

                    while(reader.hasNext()){
                        int price = Integer.parseInt(reader.nextName());
                        OrderValue val = gson.fromJson(reader, OrderValue.class);
                        bidMap.put(price, val);
                    }
                    reader.endObject();
                }
                //lettura lastId
                else if(name.equals("lastId")){
                    orderBook.lastId = reader.nextInt();
                }
                else{
                    reader.skipValue();
                }
                //non leggo stopOrder perche' sono dati non persistenti ma lo inizializzo vuoto
            }
            reader.endObject();
            System.out.println("[--ServerMain--] OrderBook loaded successfully!");
            
            //inizializzazione stopQueue vuota
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