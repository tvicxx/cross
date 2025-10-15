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
import Varie.*;


public class ServerMain{

    public static ServerSocket serverSocket;
    public static final String configFile = "src/main/java/server.properties";
    public static int TCPport;
    public static int UDPport;
    public static int maxDelay;
    public static String hostname;

    public static ConcurrentHashMap <String, Tupla> userMap = new ConcurrentHashMap<>();
    public static ConcurrentLinkedQueue <Worker> workerList = new ConcurrentLinkedQueue<>();

    public static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void loadConfig() throws FileNotFoundException, IOException{
        InputStream input = new FileInputStream(configFile);
        Properties properties = new Properties();
        properties.load(input);
        TCPport = Integer.parseInt(properties.getProperty("TCPport"));
        UDPport = Integer.parseInt(properties.getProperty("UDPport"));
        maxDelay = Integer.parseInt(properties.getProperty("maxDelay"));
        hostname = properties.getProperty("hostname");
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
            System.out.println(Ansi.YELLOW + "[--ServerMain--] Server is starting on port " + TCPport + "..." + Ansi.RESET);

            loadUserMap();

            //accept e asegna al thread pool
            while (true){
                Socket receivedSocket = serverSocket.accept();
                //System.out.println(Ansi.GREEN + "[--ServerMain--] New client connected: " + receivedSocket.getInetAddress().getHostAddress()+ Ansi.RESET);
                Worker worker = new Worker(receivedSocket, userMap);
                workerList.add(worker);
                threadPool.execute(worker);
            }
        }
        catch(IOException e){
            System.err.println("[--ServerMain--] " + e.getMessage());
        }

    }

    public static void loadUserMap() {
        try(JsonReader reader = new JsonReader(new FileReader("src/main/java/JsonFile/userMap.json"))){

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
}
