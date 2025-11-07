package Eseguibili.Server;

import Eseguibili.Server.Worker.SharedState;
import Varie.Ansi;


//gestore del timeout di inattività per ogni worker
public class TimeoutHandler implements Runnable{
    private final SharedState state; //stato condiviso con il worker associato
    private final long maxDelay; //massimo tempo di inattività consentito in millisecondi
    private Thread workerThread; //riferimento al thread del worker associato
    public String user; //utente attualmente loggato (null se nessun utente loggato)

    public TimeoutHandler(SharedState state, long maxDelay, Thread workerThread){
        this.state = state;
        this.maxDelay = maxDelay;
        this.workerThread = workerThread;
    }

    //aggiorna il timestamp dell'ultima attività del worker
    public void setTimeStamp(long timestamp){
        state.lastActivity = timestamp;
    }

    //imposta l'utente attualmente loggato
    public void setUser(String user){
        this.user = user;
    }

    public void run(){
        while(state.runningHandler.get()){
            long currentTime = System.currentTimeMillis();
            //controlla se il tempo di inattività ha superato il limite massimo consentito
            if(currentTime - state.lastActivity > maxDelay){
                if(user == null){
                    //nessun utente loggato
                    System.out.printf(Ansi.YELLOW_BACKGROUND + "[--TIMEOUT HANDLER -- WORKER %s --] No activity detected for %d minutes. Closing connection." + Ansi.RESET + "\n", workerThread.getName(), maxDelay/60000);
                    state.activeUser.set(false);
                    break;
                }
                else{
                    //utente loggato
                    System.out.printf(Ansi.YELLOW_BACKGROUND + "[--TIMEOUT HANDLER -- WORKER %s --] No activity detected from user %s for %d minutes. Logging out user and closing connection." + Ansi.RESET + "\n", workerThread.getName(), user, maxDelay/60000);
                    state.activeUser.set(false);
                    break;
                }
            }
        }
    }
}
