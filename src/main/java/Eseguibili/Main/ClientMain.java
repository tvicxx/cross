package Eseguibili.Main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;


import Eseguibili.Client.*;
import Varie.*;
import GsonClasses.*;
import GsonClasses.Commands.*;

public class ClientMain{

    private static String welcome = Ansi.BLUE +
                " $$$$$$\\  $$$$$$$\\   $$$$$$\\   $$$$$$\\   $$$$$$\\  \n" +
                "$$  __$$\\ $$  __$$\\ $$  __$$\\ $$  __$$\\ $$  __$$\\ \n" + 
                "$$ /  \\__|$$ |  $$ |$$ /  $$ |$$ /  \\__|$$ /  \\__|\n" +
                "$$ |      $$$$$$$  |$$ |  $$ |\\$$$$$$\\  \\$$$$$$\\  \n" +
                "$$ |      $$  __$$< $$ |  $$ | \\____$$\\  \\____$$\\ \n" +
                "$$ |  $$\\ $$ |  $$ |$$ |  $$ |$$\\   $$ |$$\\   $$ |\n" +
                "\\$$$$$$  |$$ |  $$ | $$$$$$  |\\$$$$$$  |\\$$$$$$  |\n" +
                " \\______/ \\__|  \\__| \\______/  \\______/  \\______/ \n" +
                "         an exChange oRder bOokS Service                \n" + 
                "\n" +
                "Created by Vicarelli Tommaso - Matr. 638912             \n" +
                "--------------------------------------------------------\n" +
                "                                                  \n" + //
                Ansi.RESET + 
                "\n[Client]" + Ansi. YELLOW + " Type 'help()' to dispaly the commands    \n" + 
                Ansi.RESET;

    private static String helpMessage = 
                Ansi.RESET + 
                "\n[Client]" + Ansi.YELLOW + " Available commands: \n" + Ansi.BLUE +
                "  - register(username, password) : register a new user \n" +
                "  - updateCredentials(username, currentPassword, newPassword) : update your credentials \n" +
                "  - login(username, password) : log in with existing user \n" +
                "  - logout() : log out of the current session \n" +
                "  - insertLimitOrder(tipo, dimensione, prezzoLimite) : send a sales/purchase order \n" +
                "  - insertMarketOrder(tipo, dimensione) : send a MarketOrder type buy/sell order \n" +
                "  - insertStopOrder(tipo, dimensione, stopPrice) : send a StopOrder type sell/buy order \n" +
                "  - cancelOrder(orderId) : cancels an existing order \n" +
                "  - getPriceHistory(mese) : retrieves the asset price history of the month in format MMYYYY \n" +
                "  - help() : displays this help message \n" +
                Ansi.RESET;


    public static final String configFile = "src/main/java/client.properties";
    public static String hostname;          // Nome host del server
    public static int TCPport;

    private static boolean udp = false;

    private static Socket SocketTCP;
    private static BufferedReader reader;
    private static PrintWriter writer;
    private static Thread receiverTCP;
    private static Thread receiverUDP;
    private static Scanner scannerInput = new Scanner(System.in);
    private static Gson gson = new Gson();
    private static GsonMess<Values> mesGson;

    //struttura dati condivisa tra thread che memorizza flag di stato e dati condivisi
    public static class SharedData{
        public AtomicBoolean isLogged = new AtomicBoolean(false);          //flag utente loggato
        public AtomicBoolean isClosed = new AtomicBoolean(false); ;        //flag connessione chiusa
        public AtomicBoolean loginError = new AtomicBoolean(false); ;      //flag errori login
        public AtomicBoolean isShuttingDown = new AtomicBoolean(false);    //flag gestione chiusura
        public volatile int UDPport = 0;                                                //flag porta UDP
    }

    public static void main(String[] args){

        SharedData shared = new SharedData();

        //Thread che si occupa di stampare sulla CLI
        Printer printer = new Printer();

        try{
            System.out.println("[Client] Loading configuration...");
            //caricamento file di configurazione
            loadConfig();

            //connessione al server TCP
            try(DatagramSocket SocketUDP  = new DatagramSocket()){
                SocketTCP = new Socket(hostname, TCPport);

                //lettura/scrittura su socket TCP
                reader = new BufferedReader(new InputStreamReader(SocketTCP.getInputStream()));
                writer = new PrintWriter(SocketTCP.getOutputStream(), true);

                //avvio thread receiver TCP
                receiverTCP = new Thread(new Receiver(SocketTCP, reader, printer, shared));
                receiverTCP.start();

                //gestione chiusura client
                Runtime.getRuntime().addShutdownHook(new Thread(){
                    public void run(){
                        if(shared.isShuttingDown.get() == false){
                            shared.isShuttingDown.set(true);
                            mesGson = new GsonMess<Values>("logout", null);
                            writer.println(gson.toJson(mesGson));

                            closeConnection();
                        }
                    }
                });

                //interfaccia utente CLI
                printer.print(welcome);
                printer.prompt();

                //ciclo di lettura comandi da CLI
                while(shared.isShuttingDown.get() == false){
                    String input = scannerInput.nextLine();

                    if(input == null || input.isEmpty()) continue;

                    //controllo validità comando
                    if(checkCommand(input)){
                        String command[] = input.split("[(),\\s]+");
                        switch(command[0]){
                            case "help":
                                printer.print(helpMessage);
                                printer.prompt();
                            break;
                            
                            case "register":
                                if(shared.isLogged.get() == false){
                                    mesGson = new GsonMess<Values>("register", new GsonUser(command[1], command[2]));
                                    writer.println(gson.toJson(mesGson));
                                }
                                else{
                                    printer.print("[Client] "+ Ansi.RED + "You are already logged in. Log out to register a new user." + Ansi.RESET);
                                    printer.prompt();
                                }
                            break;

                            case "updateCredentials":
                                if(shared.isLogged.get() == false){
                                    mesGson = new GsonMess<Values>("updateCredentials", new GsonUpdateCredentials(command[1], command[2], command[3]));
                                    writer.println(gson.toJson(mesGson));
                                }
                                else{
                                    printer.print("[Client] "+ Ansi.RED + "You are already logged in. Log out to update your credentials." + Ansi.RESET);
                                    printer.prompt();
                                }
                            break;

                            case "login":
                                mesGson = new GsonMess<Values>("login", new GsonUser(command[1], command[2]));
                                writer.println(gson.toJson(mesGson));

                                shared.loginError.set(false);
                                while(udp == false){
                                    if(shared.isLogged.get()){
                                        try{
                                            InetAddress serverAddress = InetAddress.getByName(hostname);
                                            DatagramPacket sendPacket = new DatagramPacket(new byte[1], 1, serverAddress, shared.UDPport);
                                            SocketUDP.send(sendPacket);

                                            receiverUDP = new Thread(new ReceiverUDP(SocketUDP, reader, printer));
                                            receiverUDP.start();
                                        }
                                        catch(IOException e){
                                            printer.print("[Client] " + Ansi.RED + "Error starting UDP receiver: " + e.getMessage() + Ansi.RESET);
                                            printer.prompt();

                                        }
                                        udp = true;
                                    }
                                    if(shared.loginError.get() == true) break;
                                }

                            break;

                            case "logout":
                                if(shared.isLogged.get()){
                                    mesGson = new GsonMess<Values>("logout", null);
                                    writer.println(gson.toJson(mesGson));
                                    shared.isLogged.set(false);
                                }
                                else{
                                    printer.print("[Client] "+ Ansi.RED + "You are not logged in" + Ansi.RESET);
                                    printer.prompt();
                                }
                            break;

                            case "insertLimitOrder":
                                try{
                                    String type = command[1].toLowerCase();
                                    int size = Integer.parseInt(command[2]);
                                    int limitPrice = Integer.parseInt(command[3]);
                                    if(type.equals("ask") || type.equals("bid")){
                                        if(shared.isLogged.get()){
                                            mesGson = new GsonMess<Values>("insertLimitOrder", new GsonLimitStopOrder(type, size, limitPrice));
                                            writer.println(gson.toJson(mesGson));
                                        }
                                        else{
                                            printer.print("[Client] "+ Ansi.RED + "You must be logged in to insert an order." + Ansi.RESET);
                                            printer.prompt();
                                        }
                                    }
                                    else{
                                        printer.print("[Client] "+ Ansi.RED + "Invalid order type. 'tipo' must be 'ask' or 'bid'." + Ansi.RESET);
                                        printer.prompt();
                                    }
                                }
                                catch(NumberFormatException e){
                                    printer.print("[Client] "+ Ansi.RED + "Invalid parameters for insertLimitOrder. 'dimensione' and 'prezzoLimite' must be integers." + Ansi.RESET);
                                    printer.prompt();
                                    break;
                                }
                            break;

                            case "insertMarketOrder":
                                try{
                                    String type = command[1].toLowerCase();
                                    int size = Integer.parseInt(command[2]);
                                    if(type.equals("ask") || type.equals("bid")){
                                        if(shared.isLogged.get()){
                                            mesGson = new GsonMess<Values> ("insertMarketOrder", new GsonMarketOrder(type, size));
                                            writer.println(gson.toJson(mesGson));
                                        }
                                        else{
                                            printer.print("[Client] "+ Ansi.RED + "You must be logged in to insert an order." + Ansi.RESET);
                                            printer.prompt();
                                        }
                                    }
                                    else{
                                        printer.print("[Client] "+ Ansi.RED + "Invalid order type. 'tipo' must be 'ask' or 'bid'." + Ansi.RESET);
                                        printer.prompt();
                                    }
                                }
                                catch(NumberFormatException e){
                                    printer.print("[Client] "+ Ansi.RED + "Invalid parameters for insertMarketOrder. 'dimensione' must be an integer." + Ansi.RESET);
                                    printer.prompt();
                                    break;
                                }
                            break;

                            case "insertStopOrder":
                                try{
                                    String type = command[1].toLowerCase();
                                    int size = Integer.parseInt(command[2]);
                                    int stopPrice = Integer.parseInt(command[3]);
                                    if(type.equals("ask") || type.equals("bid")){
                                        if(shared.isLogged.get()){
                                            mesGson = new GsonMess<Values>("insertStopOrder", new GsonLimitStopOrder(type, size, stopPrice));
                                            writer.println(gson.toJson(mesGson));
                                        }
                                        else{
                                            printer.print("[Client] "+ Ansi.RED + "You must be logged in to insert an order." + Ansi.RESET);
                                            printer.prompt();
                                        }
                                    }
                                    else{
                                        printer.print("[Client] "+ Ansi.RED + "Invalid order type. 'tipo' must be 'ask' or 'bid'." + Ansi.RESET);
                                        printer.prompt();
                                    }
                                }
                                catch(NumberFormatException e){
                                    printer.print("[Client] "+ Ansi.RED + "Invalid parameters for insertStopOrder. 'dimensione' and 'stopPrice' must be integers." + Ansi.RESET);
                                    printer.prompt();
                                    break;
                                }
                            break;

                            case "cancelOrder":
                                try{
                                    if(shared.isLogged.get()){
                                        int orderId = Integer.parseInt(command[1]);
                                        mesGson = new GsonMess<Values>("cancelOrder", new GsonCancelOrder(orderId));
                                        writer.println(gson.toJson(mesGson));
                                    }
                                    else{
                                        printer.print("[Client] "+ Ansi.RED + "You must be logged in to cancel an order." + Ansi.RESET);
                                        printer.prompt();
                                    }
                                }
                                catch(NumberFormatException e){
                                    printer.print("[Client] "+ Ansi.RED + "Invalid parameter for cancelOrder. 'orderId' must be an integer." + Ansi.RESET);
                                    printer.prompt();
                                    break;
                                }
                            break;

                            case "showOrderBook":
                                if(shared.isLogged.get()){
                                    mesGson = new GsonMess<Values>("showOrderBook", null);
                                    writer.println(gson.toJson(mesGson));
                                }
                                else{
                                    printer.print("[Client] "+ Ansi.RED + "You must be logged in to show the order book." + Ansi.RESET);
                                    printer.prompt();
                                }
                            break;

                            case "getPriceHistory":
                                try{
                                    if(shared.isLogged.get()){
                                        String month = command[1];
                                        mesGson = new GsonMess<Values>("getPriceHistory", new GsonPriceHistory(month));
                                        writer.println(gson.toJson(mesGson));
                                    }
                                    else{
                                        printer.print("[Client] "+ Ansi.RED + "You must be logged in to get price history." + Ansi.RESET);
                                        printer.prompt();
                                    }
                                }
                                catch(NumberFormatException e){
                                    printer.print("[Client] "+ Ansi.RED + "Invalid parameter for getPriceHistory. 'mese' must be an integer." + Ansi.RESET);
                                    printer.prompt();
                                    break;
                                }
                            break;

                            default:
                                printer.print(Ansi.RED + "[Client] Invalid command. Type 'help()' to see the list of available commands." + Ansi.RESET);
                                printer.prompt();
                            break;
                        }
                    }
                    else{
                        printer.print(Ansi.RED + "[Client] Invalid command. Type 'help()' to see the list of available commands." + Ansi.RESET);
                        printer.prompt();
                    }
                }
            }

        }
        catch(IOException e){
            printer.print(Ansi.RED + "[Client] Error connecting to server" + Ansi.RESET);
            e.printStackTrace();
        }
        catch(Exception e){
            printer.print(Ansi.RED + "[Client] Error loading configuration file: " + e.getMessage() + Ansi.RESET);
            System.exit(0);
        }

        if(shared.isShuttingDown.get() == false){
            shared.isShuttingDown.set(true);
            closeConnection();
        }
    }

    //caricamento file di configurazione
    public static void loadConfig() throws FileNotFoundException, IOException{
        InputStream input = new FileInputStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        TCPport = Integer.parseInt(prop.getProperty("TCPport"));
        hostname = prop.getProperty("hostname");
        input.close();
    }

    //controllo validità comando tramite regex
    public static boolean checkCommand(String input){
        String [] commandPatterns = {
            "register\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*\\S(?:.*\\S)?\\s*\\)$",
            "updateCredentials\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*\\S(?:.*\\S)?\\s*,\\s*\\S(?:.*\\S)?\\s*\\)$",
            "login\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*\\S(?:.*\\S)?\\s*\\)$",
            "logout\\s*\\(\\s*\\)$",
            "insertMarketOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*\\)$",
            "insertLimitOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*,\\s*\\d+(\\.\\d+)?\\s*\\)$",
            "insertStopOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*,\\s*\\d+(\\.\\d+)?\\s*\\)$",
            "cancelOrder\\s*\\(\\s*\\d+\\s*\\)$",
            "getPriceHistory\\s*\\(\\s*[0-9]+\\s*\\)$",
            "showOrderBook\\s*\\(\\s*\\)$",
            "^help\\(\\)$"
        };
        
        for(String pattern : commandPatterns){
            if(input.matches(pattern)){
                return true;
            }
        }
        return false;
    }

    //chiusura connessione client e thread associati
    public static void closeConnection(){
        try{
            if(receiverTCP != null && receiverTCP.isAlive()){
                receiverTCP.interrupt();
            }

            if(receiverUDP != null && receiverUDP.isAlive()){
                receiverUDP.interrupt();
            }

            if(SocketTCP != null && !SocketTCP.isClosed()){
                SocketTCP.close();
                }
            if(writer != null){
                writer.flush();
                writer.close();
            }
            if(reader != null){
                reader.close();
            }
        }
        catch(IOException e){
            System.err.println(Ansi.RED + "[Client] Error closing connection: " + e.getMessage() + Ansi.RESET);
        }
    }
}