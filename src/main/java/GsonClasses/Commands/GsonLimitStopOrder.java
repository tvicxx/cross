package GsonClasses.Commands;

import GsonClasses.Values;

public class GsonLimitStopOrder extends Values{
    public String type;
    public int size;
    public int price;

    public GsonLimitStopOrder(String type, int size, int price){
        this.type = type;
        this.size = size;
        this.price = price;
    }

    public String getType(){
        return type;
    }
    public int getSize(){
        return size;
    }
    public int getPrice(){
        return price;
    }
    public String toString(){
        return "{ type = " + this.type + ", size = " + this.size + ", price = " + this.price + "}";
    }
}
