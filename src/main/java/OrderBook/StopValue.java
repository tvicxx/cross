package OrderBook;

//classe che rappresenta il valore di uno stop order
public class StopValue {
    public int orderId;
    public String user;
    public String type;
    public int size;
    public int price;

    public StopValue(int orderId, String user, String type, int size, int price){
        this.orderId = orderId;
        this.user = user;
        this.type = type;
        this.size = size;
        this.price = price;
    }
}
