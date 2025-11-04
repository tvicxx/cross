package GsonClasses.Commands;

import GsonClasses.Values;

public class GsonPriceHistory extends Values{
    public String month;

    public GsonPriceHistory(String month){
        this.month = month;
    }

    public String getMonth() {
        return month;
    }

    public String toString(){
        return "{ month = " + this.month + " }"; 
    }
}
