package OrderBook;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import java.net.InetAddress;
import java.text.SimpleDateFormat;

import Eseguibili.Server.SocketUDPValue;
import Varie.Ansi;
import GsonClasses.Commands.GsonTrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

//classe che rappresenta l'order book
public class OrderBook {
    public ConcurrentSkipListMap<Integer,OrderValue> askMap; //contiene i limitOrder di tipo ask
    public int spread; //differenza tra il miglior ask e il miglior bid
    public ConcurrentLinkedQueue<StopValue> stopQueue; //contiene gli ordini di tipo stopOrder
    public ConcurrentSkipListMap<Integer,OrderValue> bidMap; //contiene i limitOrder di tipo bid
    public int lastId; //ultimo id ordine assegnato

    public static final String configFile = "src/main/java/server.properties";
    public static String orderBookPath; //percorso del file orderBook.json
    public static String storicoOrdiniPath; //percorso del file storicoOrdini.json

    public OrderBook(ConcurrentSkipListMap<Integer,OrderValue> askMap, int spread, ConcurrentLinkedQueue<StopValue> stopQueue, ConcurrentSkipListMap<Integer,OrderValue> bidMap, int lastId){
        this.askMap = askMap;
        this.spread = spread;
        this.stopQueue = stopQueue;
        this.bidMap = bidMap;
        this.lastId = lastId;

        //quando la struttura dati viene creata, carico il file di configurazione
        try{
            System.out.println("[--ServerMain--] Loading configuration...");
            loadConfig();
        }
        catch(Exception e){
            System.err.println("[--ServerMain--] Error loading configuration file: " + e.getMessage());
            System.exit(0);
        }
    }

    //metodo per caricare il file di configurazione, in particolare i percorsi dei file json
    public static void loadConfig() throws FileNotFoundException, IOException{
        InputStream input = new FileInputStream(configFile);
        Properties properties = new Properties();
        properties.load(input);
        orderBookPath = properties.getProperty("orderBookPath");
        storicoOrdiniPath = properties.getProperty("storicoOrdiniPath");
        input.close();
    }

    //metodo per aggiornare il file orderBook.json
    public synchronized void updateOrderBook(){

        //aggiorno lo spread
        if(askMap.isEmpty() == false && bidMap.isEmpty() == false){
            spread = askMap.firstKey() - bidMap.firstKey();
        }
        else if(bidMap.isEmpty() == false){
            spread = bidMap.firstKey();
        }
        else if(askMap.isEmpty() == false){
            spread = askMap.firstKey();
        }
        else{
            spread = 0;
        }


        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Crea una mappa con tutti i dati dell'order book
        Map<String, Object> orderBookData = new HashMap<>();
        orderBookData.put("askMap", askMap);
        orderBookData.put("spread", spread);
        orderBookData.put("bidMap", bidMap);
        orderBookData.put("lastId", lastId);

        //non salvo la stopQueue nel file orderBook.json perchè non è necessario visto che gli stop order vengono gestiti in memoria e non persistono dopo un riavvio del server
        
        //FileWriter senza parametri SOVRASCRIVE completamente il file
        try (FileWriter writer = new FileWriter(orderBookPath)){
            gson.toJson(orderBookData, writer);
            writer.flush(); //assicura che tutto venga scritto su disco
        }
        catch (IOException e){
            System.err.println("Errore durante l'aggiornamento dell'order book: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //funzione che gestisce un ordine di tipo ask limit
    public synchronized int askOrder(int size, int price, String user, ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        int remaining = size;
        int orderId = getLastOrderID();

        //scorro la bidMap per cercare di eseguire l'ordine
        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
            if(entry.getKey() >= price){
                //la richiesta combacia con un ordine bid esistente
                remaining = tryMatchOrder("ask", remaining, user, "bid", entry.getValue().userList, "limit", entry.getKey(), orderId, socketMapUDP);
            }
            if(remaining == 0){
                //ordine completamente eseguito, quindi procedo ad aggiornare l'order book e ritorno l'orderId
                updateOrderBook();
                return orderId;
            }

        }
        if(remaining > 0){
            //ordine eseguito parzialmente o non eseguito affatto

            //inserisco l'ordine nell'orderbook
            loadAskOrder(price, remaining, orderId, user);
        }

        //aggiorno l'order book e ritorno l'orderId
        updateOrderBook();
        return orderId;
    }

    //funzione per caricare un ordine di tipo ask limit nell'order book
    public synchronized void loadAskOrder(int price, int size, int orderId, String user){
        //creo un nuovo UserValue per l'utente che ha effettuato l'ordine
        UserValue uv = new UserValue(size, orderId, user);
        if(askMap.containsKey(price)){
            //aggiorno l'OrderValue esistente
            OrderValue ov = askMap.get(price);
            //aggiungo un nuovo UserValue alla userList
            ov.userList.add(uv);
            int newSize = ov.size + size;
            OrderValue newOv = new OrderValue(newSize, price * newSize, ov.userList);
            askMap.put(price, newOv);
        }
        else{
            //creo un nuovo OrderValue
            ConcurrentLinkedQueue<UserValue> ul = new ConcurrentLinkedQueue<UserValue>();
            ul.add(uv);
            OrderValue ov = new OrderValue(size, price * size, ul);
            askMap.put(price, ov);
        }
    }

    //funzione che gestisce un ordine di tipo bid limit
    public synchronized int bidOrder(int size, int price, String user, ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        int remaining = size;
        int orderId = getLastOrderID();

        //scorro la askMap per cercare di eseguire l'ordine
        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
            if(entry.getKey() <= price){
                //la richiesta combacia con un ordine ask esistente
                remaining = tryMatchOrder("bid", remaining, user, "ask", entry.getValue().userList, "limit", entry.getKey(), orderId, socketMapUDP);
            }
            if(remaining == 0){
                //ordine completamente eseguito, quindi procedo ad aggiornare l'order book e ritorno l'orderId
                updateOrderBook();
                return orderId;
            }
        }
        if(remaining > 0){
            //ordine eseguito parzialmente o non eseguito affatto

            //inserisco l'ordine nella orderbook
            loadBidOrder(price, remaining, orderId, user);
        }

        //aggiorno l'order book e ritorno l'orderId
        updateOrderBook();
        return orderId;
    }


    //funzione per caricare un ordine di tipo bid limit nell'order book
    public synchronized void loadBidOrder(int price, int size, int orderId, String user){
        //creo un nuovo UserValue per l'utente che ha effettuato l'ordine
        UserValue uv = new UserValue(size, orderId, user);
        if(bidMap.containsKey(price)){
            //aggiorno l'OrderValue esistente
            OrderValue ov = bidMap.get(price);
            //aggiungo un nuovo UserValue alla userList
            ov.userList.add(uv);
            int newSize = ov.size + size;
            OrderValue newOv = new OrderValue(newSize, price * newSize, ov.userList);
            bidMap.put(price, newOv);
        }
        else{
            //creo un nuovo OrderValue
            ConcurrentLinkedQueue<UserValue> ul = new ConcurrentLinkedQueue<UserValue>();
            ul.add(uv);
            OrderValue ov = new OrderValue(size, price * size, ul);
            bidMap.put(price, ov);
        }
    }

    //funzione di matching tra ordini di qualunque tipo
    //prende in input le informazioni dell'ordine in arrivo e di quello esistente nell'order book, che ha combaciato con l'ordine in arrivo
    public synchronized int tryMatchOrder(String type1, int remaining, String user1, String type2, ConcurrentLinkedQueue<UserValue> userList, String orderType, int price, int orderId, ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        //scorro la userList dell'OrderValue
        for(UserValue uv : userList){

            //user1, orderId, type1, orderType sono relativi all'ordine in arrivo
            //uv.user, uv.orderId, type2, "limit" sono relativi all'ordine esistente nell'order book

            if(uv.user.equals(user1) == false){
                if(uv.size < remaining){
                    //l'ordine di user1 viene parzialmente completato da parte di uv (uv viene completamente eseguito)
                    remaining -= uv.size;
                    
                    //messaggi udp a utenti coinvolti
                    //notifico l'user1 di avvenuta esecuzione parziale del suo ordine
                    notifyOrderUser(socketMapUDP, user1, orderId, type1, orderType, uv.size, price);
                    pushTradeToHistory(orderId, type1, orderType, uv.size , price, (int)(System.currentTimeMillis() / 1000L));

                    //notifico l'uv.user di avvenuta esecuzione totale del suo ordine
                    notifyOrderUser(socketMapUDP, uv.user, uv.orderId, type2, "limit", uv.size, price);
                    pushTradeToHistory(uv.orderId, type2, "limit", uv.size , price, (int)(System.currentTimeMillis() / 1000L));

                    System.out.println("[--OrderBook--] Matched " + uv.size + " units at price " + price + " between users " + user1 + " and " + uv.user + " for order type " + type1 + "/" + type2);

                    //rimuovo l'UserValue dalla userList
                    userList.remove(uv);
                    break;
                }
                else if(uv.size > remaining){
                    //l'ordine di user1 viene completato da parte di uv (uv viene parzialmente eseguito)
                    uv.size -= remaining;

                    //messaggi udp a utenti coinvolti
                    //notifico l'user1 di avvenuta esecuzione totale del suo ordine
                    notifyOrderUser(socketMapUDP, user1, orderId, type1, orderType, remaining, price);
                    pushTradeToHistory(orderId, type1, orderType, remaining , price, (int)(System.currentTimeMillis() / 1000L));

                    //notifica l'uv.user di avvenuta esecuzione parziale del suo ordine
                    notifyOrderUser(socketMapUDP, uv.user, uv.orderId, type2, "limit", remaining, price);
                    pushTradeToHistory(uv.orderId, type2, "limit", remaining , price, (int)(System.currentTimeMillis() / 1000L));

                    //System.out.println("[--OrderBook--] Matched " + remaining + " units at price " + price + " between users " + user1 + " and " + uv.user + " for order type " + type1 + "/" + type2);

                    remaining = 0;
                    break;
                }
                else{
                    //l'ordine di user1 viene completato da parte di uv (uv viene completamente eseguito)
                    
                    //messaggi udp a utenti coinvolti
                    //notifico l'user1 di avvenuta esecuzione totale del suo ordine
                    notifyOrderUser(socketMapUDP, user1, orderId, type1, orderType, remaining, price);
                    pushTradeToHistory(orderId, type1, orderType, remaining , price, (int)(System.currentTimeMillis() / 1000L));

                    //notifico l'uv.user di avvenuta esecuzione totale del suo ordine
                    notifyOrderUser(socketMapUDP, uv.user, uv.orderId, type2, "limit", uv.size, price);
                    pushTradeToHistory(uv.orderId, type2, "limit", uv.size , price, (int)(System.currentTimeMillis() / 1000L));

                    //System.out.println("[--OrderBook--] Matched " + uv.size + " units at price " + price + " between users " + user1 + " and " + uv.user + " for order type " + type1 + "/" + type2);

                    userList.remove(uv);
                    remaining = 0;
                    break;
                }
            }
        }
        //aggiorno l'OrderValue nell'order book
        if(userList.isEmpty()){
            //rimuovo l'OrderValue dalla map
            if(type2.equals("bid")){
                bidMap.remove(price);
            }
            else if(type2.equals("ask")){
                askMap.remove(price);
            }
        }
        else{
            //ricalcolo size e total
            int newSize = 0;
            int newTotal = 0;
            for(UserValue uv : userList){
                newSize += uv.size;
                newTotal += uv.size * price;
            }
            OrderValue newOv = new OrderValue(newSize, newTotal, userList);
            //aggiorno l'OrderValue nella map
            if(type2.equals("bid")){
                bidMap.put(price, newOv);
            }
            else if(type2.equals("ask")){
                askMap.put(price, newOv);
            }
        }
        //ritorno la quantità rimanente dell'ordine in arrivo
        return remaining;
    }

    //funzione che calcola la size totale di una mappa di ordini
    public synchronized int mapSize(ConcurrentSkipListMap<Integer, OrderValue> map){
        int totalSize = 0;
        for(Map.Entry<Integer, OrderValue> entry : map.entrySet()){
            totalSize += entry.getValue().size;
        }
        return totalSize;

    }

    //funzione che calcola la size totale degli ordini di un utente in una mappa di ordini
    public synchronized int userOrderSize(ConcurrentSkipListMap<Integer, OrderValue> map, String user){
        int userSize = 0;
        for(Map.Entry<Integer, OrderValue> entry : map.entrySet()){
            OrderValue ov = entry.getValue();
            for(UserValue uv : ov.userList){
                if(uv.user.equals(user)){
                    userSize += uv.size;
                }
            }
        }
        return userSize;
    }

    //funzione che gestisce un ordine di tipo marketOrder
    public synchronized int marketOrder(String type, int size, String user, ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        int remaining = size;

        //gli ordini di tipo market non possono essere eseguiti se l'utente non ha abbastanza ordini nella direzione opposta
        //in caso non si possa soddisfare, l'ordine viene rifiutato e viene ritornato -1

        if(type.equals("ask")){
            if(mapSize(bidMap) - userOrderSize(bidMap, user) < size){
                //l'ordine non può essere eseguito completamente
                return -1;
            }

            int orderId = getLastOrderID();

            //scorro la bidMap per cercare di eseguire l'ordine
            for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
                //la richiesta combacia con un ordine bid esistente
                remaining = tryMatchOrder(type, remaining, user, "bid", entry.getValue().userList, "market", entry.getKey(), orderId, socketMapUDP);
                if(remaining == 0){
                    //ordine completamente eseguito, quindi procedo ad aggiornare l'order book e ritorno l'orderId
                    updateOrderBook();
                    return orderId;
                }
            }
        }
        else if(type.equals("bid")){
            if(mapSize(askMap) - userOrderSize(askMap, user) < size){
                //l'ordine non può essere eseguito completamente
                return -1;
            }

            int orderId = getLastOrderID();

            //scorro la askMap per cercare di eseguire l'ordine
            for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
                //la richiesta combacia con un ordine ask esistente
                remaining = tryMatchOrder(type, remaining, user, "ask", entry.getValue().userList, "market", entry.getKey(), orderId, socketMapUDP);
                if(remaining == 0){
                    //ordine completamente eseguito, quindi procedo ad aggiornare l'order book e ritorno l'orderId
                    updateOrderBook();
                    return orderId;
                }
            }
        }
        return -1;
    }

    //funzione che controlla la stopQueue per vedere se qualche stopOrder può essere eseguito
    public synchronized void checkStopOrders(ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        //scorro la stopQueue
        for(StopValue sv : stopQueue){
            if(sv.type.equals("ask")){
                if(bidMap.isEmpty() == false){
                    //siccome bidMap è ordinata in modo decrescente, il primo elemento è il miglior bid
                    if(sv.price >= bidMap.firstKey()){
                        int remaining = sv.size;
                        //scorro la bidMap per cercare di eseguire lo stopOrder
                        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
                            //la richiesta combacia con un ordine bid esistente
                            remaining = tryMatchOrder(sv.type, remaining, sv.user, "bid", entry.getValue().userList, "market", entry.getKey(), sv.orderId, socketMapUDP);
                            if(remaining == 0){
                                //rimuovo lo stopOrder dalla coda
                                stopQueue.remove(sv);
                                updateOrderBook();
                                break;
                            }
                        }
                    }
                    break;
                }
            }
            else{
                if(askMap.isEmpty() == false){
                    //siccome askMap è ordinata in modo crescente, il primo elemento è il miglior ask
                    if(sv.price <= askMap.firstKey()){
                        int remaining = sv.size;
                        //scorro la askMap per cercare di eseguire lo stopOrder
                        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
                            //la richiesta combacia con un ordine ask esistente
                            remaining = tryMatchOrder(sv.type, remaining, sv.user, "ask", entry.getValue().userList, "market", entry.getKey(), sv.orderId, socketMapUDP);
                            if(remaining == 0){
                                //rimuovo lo stopOrder dalla coda
                                stopQueue.remove(sv);
                                updateOrderBook();
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    //funzione per ottenere un nuovo orderId
    public synchronized int getLastOrderID(){
        lastId += 1;
        return lastId;
    }

    //funzione per cancellare un ordine dato l'orderId e l'utente che ha effettuato l'ordine
    public synchronized int cancelOrder(int orderId, String user){

        //cerco l'ordine nella askMap
        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
            OrderValue ov = entry.getValue();
            for(UserValue uv : ov.userList){
                if(uv.orderId == orderId && uv.user.equals(user)){
                    //rimuovo l'ordine
                    ov.userList.remove(uv);
                    ov.size -= uv.size;
                    ov.total -= entry.getKey() * uv.size;

                    //controllo se l'OrderValue è vuoto
                    if(ov.size <= 0){
                        //rimuovo l'OrderValue dalla mappa
                        askMap.remove(entry.getKey());
                    }
                    else{
                        //aggiorno l'OrderValue nella mappa
                        askMap.put(entry.getKey(), ov);
                    }
                    updateOrderBook();
                    return 100;
                }
            }
        }

        //cerco l'ordine nella bidMap
        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
            OrderValue ov = entry.getValue();
            for(UserValue uv : ov.userList){
                if(uv.orderId == orderId && uv.user.equals(user)){
                    //rimuovo l'ordine
                    ov.userList.remove(uv);
                    ov.size -= uv.size;
                    ov.total -= entry.getKey() * uv.size;

                    //controllo se l'OrderValue è vuoto
                    if(ov.size <= 0){
                        //rimuovo l'OrderValue dalla mappa
                        bidMap.remove(entry.getKey());
                    }
                    else{
                        //aggiorno l'OrderValue nella mappa
                        bidMap.put(entry.getKey(), ov);
                    }
                    updateOrderBook();
                    return 100;
                }
            }
        }

        //cerco l'ordine nella stopQueue
        for(StopValue sv : stopQueue){
            if(sv.orderId == orderId && sv.user.equals(user)){
                //rimuovo l'ordine se trovato
                stopQueue.remove(sv);
                updateOrderBook();
                return 100;
            }
        }

        //ordine non trovato
        return 101;
    }

    //funzione per notificare un utente tramite UDP dell'avvenuta esecuzione di un ordine
    public synchronized void notifyOrderUser(ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP, String user, int orderId, String type1, String orderType, int size, int price){
        int port = -1;
        InetAddress address = null;
        //cerco la socket UDP dell'utente nella mappa
        for(Map.Entry<String,SocketUDPValue> entry : socketMapUDP.entrySet()){
            if(entry.getKey().equals(user)){
                //ho trovato la socket UDP dell'utente
                port = entry.getValue().port;
                address = entry.getValue().address;
                break;
            }
        }

        //invio il messaggio UDP all'utente
        if(port != -1 && address != null){
            try(DatagramSocket DatagramSocketUDP = new DatagramSocket()){
                //creo il messaggio Gson tramite la classe TradeNotifyUDP appena creata e lo invio sulla socket UDP
                TradeNotifyUDP trade = new TradeNotifyUDP(orderId, type1, orderType, size, price, (int)(System.currentTimeMillis() / 1000L));
                Gson gson = new Gson();
                String message = gson.toJson(trade);
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), address, port);
                DatagramSocketUDP.send(packet);
            }
            catch(Exception e){
                System.err.println(Ansi.RED + "[--OrderBook--] " + Ansi.RESET + "Error notifying user " + user + ": " + e.getMessage() + "\n");
            }
        }
        else{
            System.err.println(Ansi.RED + "[--OrderBook--] " + Ansi.RESET + "Error: could not find UDP socket for user " + user + "\n");
        }
    }

    //funzione per aggiungere una trade al file storicoOrdini.json
    public synchronized void pushTradeToHistory(int orderId, String type, String orderType, int size, int price, long timestamp){
        //aggiungo la trade al file storicoOrdini.json che è un file json contenente una chiave trades contenente un array di oggetti GsonTrade
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
        try{
            //leggo il file JSON esistente
            FileReader reader = new FileReader(storicoOrdiniPath);
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();
        
            //ottiengo l'array trades
            JsonArray tradesArray = jsonObject.getAsJsonArray("trades");
        
            //converto il nuovo ordine in JsonObject
            GsonTrade newTrade = new GsonTrade(orderId, type, orderType, size, price, timestamp);
            JsonObject newTradeJson = JsonParser.parseString(gson.toJson(newTrade)).getAsJsonObject();
        
            //aggiungo il nuovo ordine all'array
            tradesArray.add(newTradeJson);
        
            //scrivo il file aggiornato con formattazione custom
            FileWriter fileWriter = new FileWriter(storicoOrdiniPath);
            fileWriter.write("{\n  \"trades\": [\n");
        
            Gson compactGson = new Gson();
            
            //scrivo ogni trade con la formattazione desiderata
            for(int i = 0; i < tradesArray.size(); i++){
                fileWriter.write("    " + compactGson.toJson(tradesArray.get(i)));
                if(i < tradesArray.size() - 1){
                    fileWriter.write(",");
                }
                fileWriter.write("\n");
            }
        
            fileWriter.write("  ]\n}");
            fileWriter.close(); 
        }
        catch(IOException e){
            System.err.println(Ansi.RED + "[--OrderBook--] " + Ansi.RESET + "Error updating trade history: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    //funzione per ottenere lo storico dei prezzi di un dato mese e anno
    public synchronized String getPriceHistory(String month, String year){
        StringBuilder priceHistory = new StringBuilder();
        //creo una mappa per memorizzare i dati di prezzo per ogni giorno del mese
        ConcurrentSkipListMap<Integer,DayPriceData> daysMap = new ConcurrentSkipListMap<>();

        try(JsonReader reader = new JsonReader(new FileReader(storicoOrdiniPath))){
            Gson gson = new Gson();
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray tradesArray = jsonObject.getAsJsonArray("trades");
            
            //scorro l'array di trade per estrarre i dati di prezzo
            for(int i = 0; i < tradesArray.size(); i++){
                JsonObject tradeObj = tradesArray.get(i).getAsJsonObject();
                GsonTrade trade = gson.fromJson(tradeObj, GsonTrade.class);

                //estraggo la data dal timestamp
                Date date = new Date(trade.getTimestamp() * 1000L);
                SimpleDateFormat sdfMonth = new SimpleDateFormat("MM");
                SimpleDateFormat sdfYear = new SimpleDateFormat("yyyy");
                SimpleDateFormat sdfDay = new SimpleDateFormat("dd");
                String tradeMonth = sdfMonth.format(date);
                String tradeYear = sdfYear.format(date);
                String tradeDay = sdfDay.format(date);

                //controllo se il trade appartiene al mese e anno richiesti
                if(tradeMonth.equals(String.format("%02d", Integer.parseInt(month))) && tradeYear.equals(year)){
                    int day = Integer.parseInt(tradeDay);
                    DayPriceData dpd = daysMap.getOrDefault(day, new DayPriceData());
                    dpd.updatePrices(trade.getPrice());
                    daysMap.put(day, dpd);
                }
            }

            //costruisco la stringa del price history già pronta per la stampa, che poi verrà ritornata al client

            //inserisco l'intestazione
            priceHistory.append(String.format("\n" + Ansi.YELLOW_BACKGROUND + "Price History for %02d/%s:" + Ansi.RESET + "\n", Integer.parseInt(month), year));
            priceHistory.append(Ansi.BLUE_BACKGROUND + "Day  |  Open  |  Close  |  High  |  Low " + Ansi.RESET+ "\n");
            priceHistory.append("---------------------------------------\n");
            int bool = 0;
            //inserisco i dati di ogni giorno
            for(Map.Entry<Integer, DayPriceData> entry : daysMap.entrySet()){
                int day = entry.getKey();
                DayPriceData dpd = entry.getValue();

                //alterno il colore di sfondo per ogni riga
                if(bool == 0){
                    priceHistory.append(String.format(Ansi.BLACK_BACKGROUND + "%02d   |  %5d |  %6d |  %5d |  %4d " + Ansi.RESET+ "\n", day, dpd.openPrice, dpd.closePrice, dpd.highPrice, dpd.lowPrice));
                    bool = 1;
                }
                else {
                    priceHistory.append(String.format(Ansi.WHITE_BACKGROUND + "%02d   |  %5d |  %6d |  %5d |  %4d " + Ansi.RESET+ "\n", day, dpd.openPrice, dpd.closePrice, dpd.highPrice, dpd.lowPrice));
                    bool = 0;
                }
            }
            priceHistory.append("---------------------------------------\n");

        }
        catch(IOException e){
            System.err.println(Ansi.RED + "[--OrderBook--] " + Ansi.RESET + "Error retrieving price history: " + e.getMessage() + "\n");
            e.printStackTrace();
        }

        return priceHistory.toString();
    }

    //funzione per mostrare l'order book in formato stringa correttamente formattata
    public synchronized String showOrderBook(){
        StringBuilder obString = new StringBuilder();
        //inserisco l'intestazione
        obString.append("\n" + Ansi.YELLOW_BACKGROUND + "Current Order Book:" + Ansi.RESET + "\n");
        obString.append(Ansi.RED_BACKGROUND + "   ASK ORDERS   " + Ansi.RESET + "\n");
        obString.append(Ansi.WHITE_BACKGROUND + "Price |  Size  " + Ansi.RESET + "\n");
        obString.append("----------------\n");
        int bool = 0;
        //inserisco gli ordini ask
        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
            int price = entry.getKey();
            OrderValue ov = entry.getValue();
            if(bool == 0){
                obString.append(String.format(Ansi.BLACK_BACKGROUND + "%5d | %5d " + Ansi.RESET+ "\n", price, ov.size));
                bool = 1;
            }
            else {
                obString.append(String.format(Ansi.WHITE_BACKGROUND + "%5d | %5d " + Ansi.RESET+ "\n", price, ov.size));
                bool = 0;
            }
        }
        obString.append("----------------\n");
        //inserisco lo spread e lo coloro in base al suo valore
        if(spread < 0) obString.append(String.format(Ansi.WHITE_BACKGROUND + "Current Spread: " + Ansi.RED_BACKGROUND + "%d" + Ansi.RESET + "\n", spread));
        else obString.append(String.format(Ansi.WHITE_BACKGROUND + "Current Spread: " + Ansi.GREEN_BACKGROUND + "%d" + Ansi.RESET + "\n", spread));
        obString.append("----------------\n");
        obString.append(Ansi.GREEN_BACKGROUND + "   BID ORDERS   " + Ansi.RESET + "\n");
        obString.append(Ansi.WHITE_BACKGROUND + "Price |  Size  " + Ansi.RESET + "\n");
        obString.append("----------------\n");
        bool = 0;
        //inserisco gli ordini bid
        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
            int price = entry.getKey();
            OrderValue ov = entry.getValue();
            if(bool == 0){
                obString.append(String.format(Ansi.BLACK_BACKGROUND + "%5d | %5d " + Ansi.RESET+ "\n", price, ov.size));
                bool = 1;
            }
            else{
                obString.append(String.format(Ansi.WHITE_BACKGROUND + "%5d | %5d " + Ansi.RESET+ "\n", price, ov.size));
                bool = 0;
            }
        }
        obString.append("----------------\n");
        obString.append(Ansi.BLUE_BACKGROUND + "   STOP ORDERS   " + Ansi.RESET + "\n");
        obString.append(Ansi.WHITE_BACKGROUND + "Type | Price | Size | User " + Ansi.RESET + "\n");
        obString.append("--------------------------------\n");
        bool = 0;
        //inserisco gli ordini stopOrder
        for(StopValue sv : stopQueue){
            if(bool == 0){
                obString.append(String.format(Ansi.BLACK_BACKGROUND + "%4s | %5d | %4d | %s " + Ansi.RESET+ "\n", sv.type.toUpperCase(), sv.price, sv.size, sv.user));
                bool = 1;
            }
            else{
                obString.append(String.format(Ansi.WHITE_BACKGROUND + "%4s | %5d | %4d | %s " + Ansi.RESET+ "\n", sv.type.toUpperCase(), sv.price, sv.size, sv.user));
                bool = 0;
            }
        }
        obString.append("--------------------------------\n");
        return obString.toString();
    }
}
