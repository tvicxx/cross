package GsonClasses.Responses;

import java.io.PrintWriter;

import com.google.gson.Gson;

//classe che rappresenta una risposta di un ordine in formato Gson
public class GsonResponseOrder{
    public int orderId;

    public void setResponseOrder(String orderId){
        this.orderId = Integer.parseInt(orderId);
    }

    public void sendMessage(Gson gson,PrintWriter writer){
        String respMessage = gson.toJson(this);
        writer.println(respMessage);
    }
}
