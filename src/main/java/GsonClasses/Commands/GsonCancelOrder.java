package GsonClasses.Commands;

import GsonClasses.Values;

//classe per la serializzazione della richiesta di cancellazione di un ordine
public class GsonCancelOrder extends Values{
    public int orderId;

    public GsonCancelOrder(int orderId){
        this.orderId = orderId;
    }

    public int getOrderId(){
        return orderId;
    }

    public String toString(){
        return "{ orderId = " + this.orderId + "}";
    }
}
