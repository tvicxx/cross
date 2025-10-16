package Eseguibili.Main;

import java.io.*;
import java.net.*;
import java.util.*;
//import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

//import org.ietf.jgss.GSSException;

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
                "  - getPriceHistory(mese) : retrieves the asset price history of the month \n" +
                "  - help() : displays this help message \n" +
                Ansi.RESET;


    public static final String configFile = "src/main/java/client.properties";
    public static String hostname;          // Nome host del server
    public static int TCPport;

    private static Socket SocketTCP;
    private static BufferedReader reader;
    private static PrintWriter writer;
    private static Thread receiverTCP;
    private static Scanner scannerInput = new Scanner(System.in);
    private static GsonMess<Values> mesGson;

    public static class SharedData{
        public AtomicBoolean isLogged = new AtomicBoolean(false);          //flag utente loggato
        public AtomicBoolean isClosed = new AtomicBoolean(false); ;        //flag connessione chiusa
        public AtomicBoolean loginError = new AtomicBoolean(false); ;      //flag errori login
        public AtomicBoolean isShuttingDown = new AtomicBoolean(false);    //flag gestione chiusura
        public volatile int UDPport = 0;                                                //flag porta UDP
    }

    public static void main(String[] args){

        SharedData shared = new SharedData();

        try{
            System.out.println("[Client] Loading configuration...");
            loadConfig();

            //Thread che si occupa di stampare sulla CLI
            Printer printer = new Printer();

            try(DatagramSocket SocketUDP  = new DatagramSocket()){
                SocketTCP = new Socket(hostname, TCPport);

                reader = new BufferedReader(new InputStreamReader(SocketTCP.getInputStream()));
                writer = new PrintWriter(SocketTCP.getOutputStream(), true);

                receiverTCP = new Thread(new Receiver(SocketTCP, reader, printer, shared));
                receiverTCP.start();

                printer.print(welcome);
                printer.prompt();
                

                while(shared.isShuttingDown.get() == false){
                    String input = scannerInput.nextLine();

                    if(input == null || input.isEmpty()) continue;

                    if(checkCommand(input)){
                        String command[] = input.split("[(),\\s]+");
                        switch(command[0]){
                            case "help":
                                printer.print(helpMessage);
                                printer.prompt();
                            break;
                            
                            case "register":
                                mesGson = new GsonMess<Values>("register", new GsonUser(command[1], command[2]));
                                writer.println(mesGson.toString());
                            break;

                            case "updateCredentials":
                                if(shared.isLogged.get() == false){
                                    mesGson = new GsonMess<Values>("updateCredentials", new GsonUpdateCredentials(command[1], command[2], command[3]));
                                    writer.println(mesGson.toString());
                                }
                                else{
                                    printer.print("[Client] "+ Ansi.RED + "You are already logged in. Log out to update your credentials." + Ansi.RESET);
                                    printer.prompt();
                                }
                            break;

                            case "login":
                                mesGson = new GsonMess<Values>("login", new GsonUser(command[1], command[2]));
                                writer.println(mesGson.toString());

                            break;

                            case "logout":
                                if(shared.isLogged.get()){
                                    mesGson = new GsonMess<Values>("logout", null);
                                    writer.println(mesGson.toString());
                                    shared.isLogged.set(false);
                                }
                                else{
                                    printer.print("[Client] "+ Ansi.RED + "You are not logged in" + Ansi.RESET);
                                    printer.prompt();
                                }
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
            System.err.println(Ansi.RED + "[Client] Error connecting to server" + Ansi.RESET);
            e.printStackTrace();
        }
        catch(Exception e){
            System.err.println(Ansi.RED + "[Client] Error loading configuration file: " + e.getMessage() + Ansi.RESET);
            System.exit(0);
        }
    }

    public static void loadConfig() throws FileNotFoundException, IOException{
        InputStream input = new FileInputStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        TCPport = Integer.parseInt(prop.getProperty("TCPport"));
        hostname = prop.getProperty("hostname");
        input.close();
    }

    public static boolean checkCommand(String input){
        //implementare controllo comandi
        return true;
    }
}
