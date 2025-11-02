package GsonClasses.Commands;

import GsonClasses.Values;

public class GsonPriceHistory extends Values{
    public int month;
    public int year;

    public GsonPriceHistory(int month, int year){
        this.month = month;
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public int getYear() {
        return year;
    }

    public String toString(){
        return "{ month = " + this.month + ", year = " + this.year + "}"; 
    }
}
