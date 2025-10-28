package OrderBook;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OrderBook {
    public ConcurrentSkipListMap<Integer,OrderValue> askMap;
    public int spread;
    public ConcurrentLinkedQueue<StopValue> stopQueue;
    public ConcurrentSkipListMap<Integer,OrderValue> bidMap;
    public int lastId;

    public static final String configFile = "src/main/java/server.properties";
    public static String orderBookPath;

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
        //orderBookData.put("lastOrderId", lastOrderId);
        orderBookData.put("askMap", askMap);
        orderBookData.put("spread", spread);
        orderBookData.put("bidMap", bidMap);
        orderBookData.put("lastId", lastId);
        //orderBookData.put("stopQueue", stopQueue);
        
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

    public synchronized int askOrder(int size, int price, String user){
        int remaining = size;
        int orderId = getLastOrderID();
        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
            if(entry.getKey() >= price){
                //la richiesta combacia con un ordine bid esistente
                remaining = tryMatchOrder("ask", remaining, user, "bid", entry.getValue().userList, "limit", entry.getKey(), orderId);
            }
            if(remaining == 0){
                updateOrderBook();
                return orderId;
            }
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
            askMap.put(price, ov);
        }
    }

    public synchronized int bidOrder(int size, int price, String user){
        int remaining = size;
        int orderId = getLastOrderID();
        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
            if(entry.getKey() <= price){
                remaining = tryMatchOrder("bid", remaining, user, "ask", entry.getValue().userList, "limit", entry.getKey(), orderId);
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

    public synchronized int tryMatchOrder(String type1, int remaining, String user1, String type2, ConcurrentLinkedQueue<UserValue> userList, String orderType, int price, int orderId){
        //scorro la userList dell'OrderValue
        for(UserValue uv : userList){
            if(uv.user.equals(user1) == false){
                if(uv.size < remaining){
                    remaining -= uv.size;
                    //messaggi udp a utenti coinvolti
                    //per ora stampo sul server
                    System.out.println("[--OrderBook--] Matched " + uv.size + " units at price " + price + " between users " + user1 + " and " + uv.user + " for order type " + type1 + "/" + type2);

                    //rimuovo l'UserValue dalla userList
                    userList.remove(uv);
                }
                else if(uv.size > remaining){
                    uv.size -= remaining;

                    //messaggi udp a utenti coinvolti
                    //per ora stampo sul server
                    System.out.println("[--OrderBook--] Matched " + remaining + " units at price " + price + " between users " + user1 + " and " + uv.user + " for order type " + type1 + "/" + type2);

                    remaining = 0;
                    break;
                }
                else{
                    //messaggi udp a utenti coinvolti
                    //per ora stampo sul server
                    System.out.println("[--OrderBook--] Matched " + uv.size + " units at price " + price + " between users " + user1 + " and " + uv.user + " for order type " + type1 + "/" + type2);

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

    public synchronized int marketOrder(String type, int size, String user){
        int remaining = size;

        if(type.equals("ask")){
            if(mapSize(bidMap) - userOrderSize(bidMap, user) < size){
                return -1;
            }

            int orderId = getLastOrderID();

            for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
                remaining = tryMatchOrder(type, remaining, user, "bid", entry.getValue().userList, "market", entry.getKey(), orderId);
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
                remaining = tryMatchOrder(type, remaining, user, "ask", entry.getValue().userList, "market", entry.getKey(), orderId);
                if(remaining == 0){
                    updateOrderBook();
                    return orderId;
                }
            }
        }
        return -1;
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

        return 101;
    }
}
