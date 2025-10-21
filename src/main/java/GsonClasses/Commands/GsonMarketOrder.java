package GsonClasses.Commands;

import GsonClasses.Values;

public class GsonMarketOrder extends Values{
    public String type;
    public int size;

    public GsonMarketOrder(String type, int size){
        this.type = type;
        this.size = size;
    }

    public String getType(){
        return type;
    }
    public int getSize(){
        return size;
    }

    public String toString(){
        return "{ type = " + this.type + ", size = " + this.size + "}";
    }
}
