package OrderBook;

import java.util.concurrent.ConcurrentLinkedQueue;

public class OrderValue {
    public int size;
    public int total;
    public ConcurrentLinkedQueue<UserValue> userList;

    public OrderValue(int size, int total, ConcurrentLinkedQueue<UserValue> userList){
        this.size = size;
        this.total = total;
        this.userList = userList;
    }

    public String toString(){
        return "{size = " + this.size + ", total = " + this.total + ", userList = " + this.userList.toString() + "}";
    }
}
