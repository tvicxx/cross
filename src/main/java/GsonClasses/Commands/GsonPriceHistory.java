package GsonClasses.Commands;

import GsonClasses.Values;

//classe per la serializzazione della richiesta di price history
public class GsonPriceHistory extends Values{
    public String month; //formato MMYYYY

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
