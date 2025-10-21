package GsonClasses.Responses;

import GsonClasses.Values;

public class GsonResponseOrder extends Values{
    public int orderId;

    public GsonResponseOrder(int orderId){
        this.orderId = orderId;
    }

    public int getOrderId(){
        return this.orderId;
    }

    public String toString(){
        return "Order ID: " + this.orderId;
    }
}
