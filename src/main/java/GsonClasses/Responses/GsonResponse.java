package GsonClasses.Responses;

import java.io.PrintWriter;

import com.google.gson.Gson;

//classe che rappresenta una risposta generica in formato Gson
public class GsonResponse{
    public String type;
    public int response;
    public String errorMessage;

    public void setResponse(String type, int response, String errorMessage){
        this.type = type;
        this.response = response;
        this.errorMessage = errorMessage;
    }

    public void sendMessage(Gson gson,PrintWriter writer){
        String respMessage = gson.toJson(this);
        writer.println(respMessage);
    }
}