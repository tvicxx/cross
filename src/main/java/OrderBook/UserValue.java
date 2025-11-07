package OrderBook;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//classe che rappresenta il valore associato a un utente in un ordine dell'OrderBook
public class UserValue {
    public int size;
    public int orderId;
    public String user;
    public String date;

    public UserValue(int size, int orderId, String user){
        this.size = size;
        this.orderId = orderId;
        this.user = user;
        
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        this.date = now.format(formatter);
    }
}
