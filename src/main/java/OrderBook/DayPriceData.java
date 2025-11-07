package OrderBook;

//classe che memorizza i prezzi di apertura, chiusura, massimo e minimo di una giornata, usata per l'analisi dei dati storici
public class DayPriceData {
    public int openPrice;
    public int closePrice;
    public int highPrice;
    public int lowPrice;

    public void updatePrices(int price) {
        if (openPrice == 0) {
            openPrice = price;
            highPrice = price;
            lowPrice = price;
            closePrice = price;
        } else {
            if (price > highPrice) {
                highPrice = price;
            }
            if (price < lowPrice) {
                lowPrice = price;
            }
            closePrice = price;
        }
    }
}
