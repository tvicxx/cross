package OrderBook;

import java.util.concurrent.ConcurrentLinkedQueue;

//classe che rappresenta il valore di un ordine contenuto nell'orderBook, inclusi size, total e la lista degli utenti associati
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
