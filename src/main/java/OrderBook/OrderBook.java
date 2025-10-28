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

    public static final String configFile = "src/main/java/server.properties";
    public static String orderBookPath;

    public OrderBook(ConcurrentSkipListMap<Integer,OrderValue> askMap, int spread, ConcurrentLinkedQueue<StopValue> stopQueue, ConcurrentSkipListMap<Integer,OrderValue> bidMap){
        this.askMap = askMap;
        this.spread = spread;
        this.stopQueue = stopQueue;
        this.bidMap = bidMap;

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
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Crea una mappa con tutti i dati dell'order book
        Map<String, Object> orderBookData = new HashMap<>();
        //orderBookData.put("lastOrderId", lastOrderId);
        orderBookData.put("askMap", askMap);
        orderBookData.put("spread", spread);
        orderBookData.put("bidMap", bidMap);
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
        int orderId = (updateLastOrderID()) + 1;
        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
            if(entry.getKey() >= price){
                //la richiesta combacia con un ordine bid esistente
            }
            if(remaining == 0){
                updateOrderBook();
                return orderId;
            }
        }
        if(remaining > 0){
            //inserisco l'ordine nella orderbook
            loadAskOrder(price, remaining, orderId, user);
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
        int orderId = (updateLastOrderID()) +1;
        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
            if(entry.getKey() <= price){
                //la richiesta combacia con un ordine ask esistente
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

    public synchronized int updateLastOrderID(){
        //scorre tutta la orderbook per trovare l'ultimo orderId
        int maxOrderId = 0;
        for(Map.Entry<Integer, OrderValue> entry : askMap.entrySet()){
            OrderValue ov = entry.getValue();
            for(UserValue uv : ov.userList){
                if(uv.orderId > maxOrderId){
                    maxOrderId = uv.orderId;
                }
            }
        }
        for(Map.Entry<Integer, OrderValue> entry : bidMap.entrySet()){
            OrderValue ov = entry.getValue();
            for(UserValue uv : ov.userList){
                if(uv.orderId > maxOrderId){
                    maxOrderId = uv.orderId;
                }
            }
        }

        //controlla anche la stopQueue

        return maxOrderId;
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
