package OrderBook;

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
