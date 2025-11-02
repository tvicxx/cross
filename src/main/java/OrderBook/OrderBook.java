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

public class OrderBook {
    public ConcurrentSkipListMap<Integer,OrderValue> askMap;
    public int spread;
    public ConcurrentLinkedQueue<StopValue> stopQueue;
    public ConcurrentSkipListMap<Integer,OrderValue> bidMap;
    public int lastId;

    public static final String configFile = "src/main/java/server.properties";
    public static String orderBookPath;
    public static String storicoOrdiniPath;

    public OrderBook(ConcurrentSkipListMap<Integer,OrderValue> askMap, int spread, ConcurrentLinkedQueue<StopValue> stopQueue, ConcurrentSkipListMap<Integer,OrderValue> bidMap, int lastId){
        this.askMap = askMap;
        this.spread = spread;
        this.stopQueue = stopQueue;
        this.bidMap = bidMap;
        this.lastId = lastId;

        try{
            System.out.println("[--ServerMain--] Loading configuration...");
            loadConfig();
        }
        catch(Exception e){
            System.err.println("[--ServerMain--] Error loading configuration file: " + e.getMessage());
            System.exit(0);
        }
    }

    public static void loadConfig() throws FileNotFoundException, IOException{
        InputStream input = new FileInputStream(configFile);
        Properties properties = new Properties();
        properties.load(input);
        orderBookPath = properties.getProperty("orderBookPath");
        storicoOrdiniPath = properties.getProperty("storicoOrdiniPath");
        input.close();
    }

    public synchronized void updateOrderBook(){
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
        //inserimento stopQueue in array per serializzazione
        
        // FileWriter senza parametri SOVRASCRIVE completamente il file
        try (FileWriter writer = new FileWriter(orderBookPath)){
            gson.toJson(orderBookData, writer);
            writer.flush(); // Assicura che tutto venga scritto su disco
        }
        catch (IOException e){
            System.err.println("Errore durante l'aggiornamento dell'order book: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized int askOrder(int size, int price, String user, ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        int remaining = size;
        int orderId = getLastOrderID();
        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
            if(entry.getKey() >= price){
                //la richiesta combacia con un ordine bid esistente
                remaining = tryMatchOrder("ask", remaining, user, "bid", entry.getValue().userList, "limit", entry.getKey(), orderId, socketMapUDP);
            }
            if(remaining == 0){
                updateOrderBook();
                return orderId;
            }

        System.out.printf(Ansi.RED_BACKGROUND + "Inserting limit ask order: size %d, price %d, user %s\n" + Ansi.RESET, size, price, user);
        }
        if(remaining > 0){
            //inserisco l'ordine nella orderbook
            loadAskOrder(price, remaining, orderId, user);
            //aggiorno lo spread
            if(bidMap.isEmpty() == false){
                spread = askMap.firstKey() - bidMap.firstKey();
            }
            else{
                spread = askMap.firstKey();
            }
        }
        updateOrderBook();
        return orderId;
    }

    public synchronized void loadAskOrder(int price, int size, int orderId, String user){
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

    public synchronized int bidOrder(int size, int price, String user, ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        int remaining = size;
        int orderId = getLastOrderID();
        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
            if(entry.getKey() <= price){
                remaining = tryMatchOrder("bid", remaining, user, "ask", entry.getValue().userList, "limit", entry.getKey(), orderId, socketMapUDP);
            }
            if(remaining == 0){
                updateOrderBook();
                return orderId;
            }
        }
        if(remaining > 0){
            //inserisco l'ordine nella orderbook
            loadBidOrder(price, remaining, orderId, user);
        }
        updateOrderBook();
        return orderId;
    }

    public synchronized void loadBidOrder(int price, int size, int orderId, String user){
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

    public synchronized int tryMatchOrder(String type1, int remaining, String user1, String type2, ConcurrentLinkedQueue<UserValue> userList, String orderType, int price, int orderId, ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        //scorro la userList dell'OrderValue
        for(UserValue uv : userList){
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
            int newSize = 0;
            int newTotal = 0;
            for(UserValue uv : userList){
                newSize += uv.size;
                newTotal += uv.size * price;
            }
            OrderValue newOv = new OrderValue(newSize, newTotal, userList);
            if(type2.equals("bid")){
                bidMap.put(price, newOv);
            }
            else if(type2.equals("ask")){
                askMap.put(price, newOv);
            }
        }
        return remaining;
    }

    public synchronized int mapSize(ConcurrentSkipListMap<Integer, OrderValue> map){
        int totalSize = 0;
        for(Map.Entry<Integer, OrderValue> entry : map.entrySet()){
            totalSize += entry.getValue().size;
        }
        return totalSize;

    }

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

    public synchronized int marketOrder(String type, int size, String user, ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        int remaining = size;

        if(type.equals("ask")){
            if(mapSize(bidMap) - userOrderSize(bidMap, user) < size){
                return -1;
            }

            int orderId = getLastOrderID();

            for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
                remaining = tryMatchOrder(type, remaining, user, "bid", entry.getValue().userList, "market", entry.getKey(), orderId, socketMapUDP);
                if(remaining == 0){
                    updateOrderBook();
                    return orderId;
                }
            }
        }
        else if(type.equals("bid")){
            if(mapSize(askMap) - userOrderSize(askMap, user) < size){
                return -1;
            }

            int orderId = getLastOrderID();
            for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
                remaining = tryMatchOrder(type, remaining, user, "ask", entry.getValue().userList, "market", entry.getKey(), orderId, socketMapUDP);
                if(remaining == 0){
                    updateOrderBook();
                    return orderId;
                }
            }
        }
        return -1;
    }

    public synchronized void checkStopOrders(ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP){
        //scorro la stopQueue
        for(StopValue sv : stopQueue){
            if(sv.type.equals("ask")){
                if(bidMap.isEmpty() == false){
                    if(sv.price >= bidMap.firstKey()){
                        int remaining = sv.size;
                        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
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
                    if(sv.price <= askMap.firstKey()){
                        int remaining = sv.size;
                        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
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

    public synchronized int getLastOrderID(){
        lastId += 1;
        return lastId;
    }

    public synchronized int cancelOrder(int orderId, String user){
        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
            OrderValue ov = entry.getValue();
            for(UserValue uv : ov.userList){
                if(uv.orderId == orderId && uv.user.equals(user)){
                    //rimuovo l'ordine
                    ov.userList.remove(uv);
                    ov.size -= uv.size;
                    ov.total -= entry.getKey() * uv.size;
                    if(ov.size <= 0){
                        askMap.remove(entry.getKey());
                    }
                    else{
                        askMap.put(entry.getKey(), ov);
                    }
                    updateOrderBook();
                    return 100;
                }
            }
        }

        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
            OrderValue ov = entry.getValue();
            for(UserValue uv : ov.userList){
                if(uv.orderId == orderId && uv.user.equals(user)){
                    //rimuovo l'ordine
                    ov.userList.remove(uv);
                    ov.size -= uv.size;
                    ov.total -= entry.getKey() * uv.size;
                    if(ov.size <= 0){
                        bidMap.remove(entry.getKey());
                    }
                    else{
                        bidMap.put(entry.getKey(), ov);
                    }
                    updateOrderBook();
                    return 100;
                }
            }
        }

        //implementare controllo in stopOrder
        for(StopValue sv : stopQueue){
            if(sv.orderId == orderId && sv.user.equals(user)){
                stopQueue.remove(sv);
                updateOrderBook();
                return 100;
            }
        }

        return 101;
    }

    public synchronized void notifyOrderUser(ConcurrentSkipListMap<String, SocketUDPValue> socketMapUDP, String user, int orderId, String type1, String orderType, int size, int price){
        int port = -1;
        InetAddress address = null;
        for(Map.Entry<String,SocketUDPValue> entry : socketMapUDP.entrySet()){
            if(entry.getKey().equals(user)){
                port = entry.getValue().port;
                address = entry.getValue().address;
                break;
            }
        }
        if(port != -1 && address != null){
            try(DatagramSocket DatagramSocketUDP = new DatagramSocket()){
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
        
            Gson compactGson = new Gson(); // Gson senza pretty printing per gli oggetti dell'array
        
            for(int i = 0; i < tradesArray.size(); i++){
                fileWriter.write("    " + compactGson.toJson(tradesArray.get(i)));
                if(i < tradesArray.size() - 1){
                    fileWriter.write(",");
                }
                fileWriter.write("\n");
            }
        
            fileWriter.write("  ]\n}");
            fileWriter.close();

            //System.out.println(Ansi.GREEN + "[--OrderBook--] " + Ansi.RESET + "Trade added to history: " + newTrade.toString() + "\n");
        
        }
        catch(IOException e){
            System.err.println(Ansi.RED + "[--OrderBook--] " + Ansi.RESET + "Error updating trade history: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    public synchronized String getPriceHistory(String month, String year){
        StringBuilder priceHistory = new StringBuilder();
        ConcurrentSkipListMap<Integer,DayPriceData> daysMap = new ConcurrentSkipListMap<>();

        try(JsonReader reader = new JsonReader(new FileReader(storicoOrdiniPath))){
            Gson gson = new Gson();
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray tradesArray = jsonObject.getAsJsonArray("trades");
            
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

            //costruisco la stringa del price history

            //inserisco l'intestazione
            priceHistory.append(String.format("\n" + Ansi.YELLOW_BACKGROUND + "Price History for %02d/%s:" + Ansi.RESET + "\n", Integer.parseInt(month), year));
            priceHistory.append(Ansi.BLUE_BACKGROUND + "Day  |  Open  |  Close  |  High  |  Low " + Ansi.RESET+ "\n");
            priceHistory.append("---------------------------------------\n");
            int bool = 0;
            for(Map.Entry<Integer, DayPriceData> entry : daysMap.entrySet()){
                int day = entry.getKey();
                DayPriceData dpd = entry.getValue();
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
}
