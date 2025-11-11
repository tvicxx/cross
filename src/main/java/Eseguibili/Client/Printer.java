package Eseguibili.Client;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import Varie.*;

public class Printer{
    //Classe responsabile della stampa dei messaggi ricevuti dal server in modo asincrono.

    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(); //coda per la memorizzazione dei messaggi da stampare
    private final Thread printerThread;
    private volatile boolean running = false; //indica se il prompt è attivo

    public Printer(){
        printerThread = new Thread(() -> {
            while(Thread.currentThread().isInterrupted() == false){
                try{
                    //prelevo il messaggio dalla coda e lo stampo
                    String message = messageQueue.take();
                    System.out.println(message);

                    //se il prompt è attivo, ristampo il prompt
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
        //imposto il thread come daemon in modo che non impedisca la terminazione del programma
        printerThread.setDaemon(true);
        printerThread.start();
    }

    //metodo per inviare un messaggio da stampare
    public void print(String message){
        try{
            messageQueue.put(message);
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    //metodo per stampare il prompt
    public void prompt(){
        running = true;
        if(messageQueue.isEmpty()){
            System.out.print(Ansi.RESET + ">>> ");
            System.out.flush();
        }
    }
}