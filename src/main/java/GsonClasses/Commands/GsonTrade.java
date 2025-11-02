package GsonClasses.Commands;

import GsonClasses.Values;

public class GsonTrade extends Values{
    private int orderId;
    private String type;
    private String orderType;
    private int size;
    private int price;
    private long timestamp;

    public GsonTrade(int orderId, String type, String orderType, int size, int price, long timestamp){
        this.orderId = orderId;
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.price = price;
        this.timestamp = timestamp;
    }

    public int getOrderId(){
        return orderId;
    }
    public String getType(){
        return type;
    }

    public String getOrderType(){
        return orderType;
    }
    
    public int getSize(){
        return size;
    }

    public int getPrice(){
        return price;
    }

    public long getTimestamp(){
        return timestamp;
    }

    public String toString(){
        return "GsonTrade [orderId=" + orderId + ", type=" + type + ", orderType=" + orderType + ", size=" + size + ", price=" + price + ", timestamp=" + timestamp + "]";
    }
}
