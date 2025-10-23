package GsonClasses.Responses;

import java.io.PrintWriter;

import com.google.gson.Gson;

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
