package OrderBook;

public class TradeNotifyUDP {
    public int orderId;
    public String type;
    public String orderType;
    public int size;
    public int price;
    public int timestamp;

    public TradeNotifyUDP(int orderId, String type, String orderType, int size, int price, int timestamp){
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
}
