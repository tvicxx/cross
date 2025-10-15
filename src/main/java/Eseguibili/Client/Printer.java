package Eseguibili.Client;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import Varie.*;

public class Printer{
    //Classe responsabile della stampa dei messaggi ricevuti dal server in modo asincrono.

    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final Thread printerThread;
    private volatile boolean running = false;

    public Printer(){
        printerThread = new Thread(() -> {
            while(Thread.currentThread().isInterrupted() == false){
                try{
                    String message = messageQueue.take();
                    System.out.println(message);

                    if(running){
                        System.out.print(Ansi.RESET + ">>> ");
                    }
                }
                catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        printerThread.setDaemon(true);
        printerThread.start();
    }

    public void print(String message) {
        try{
            messageQueue.put(message);
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }
    public void prompt(){
        running = true;
        if(messageQueue.isEmpty()){
            System.out.print(Ansi.RESET + ">>> ");
            System.out.flush();
        }
    }
}
