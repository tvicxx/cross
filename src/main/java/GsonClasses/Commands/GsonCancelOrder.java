package GsonClasses.Commands;

import GsonClasses.Values;

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
