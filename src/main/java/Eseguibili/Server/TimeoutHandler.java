package Eseguibili.Server;

import Eseguibili.Server.Worker.SharedState;
import Varie.Ansi;

public class TimeoutHandler implements Runnable{
    private final SharedState state;
    private final long maxDelay;
    private Thread workerThread;
    public String user;

    public TimeoutHandler(SharedState state, long maxDelay, Thread workerThread){
        this.state = state;
        this.maxDelay = maxDelay;
        this.workerThread = workerThread;
    }

    public void setTimeStamp(long timestamp){
        state.lastActivity = timestamp;
    }

    public void setUser(String user){
        this.user = user;
    }

    public void run(){
        while(state.runningHandler.get()){
            long currentTime = System.currentTimeMillis();
            if(currentTime - state.lastActivity > maxDelay){
                if(user == null){
                    System.out.printf(Ansi.YELLOW_BACKGROUND + "[--TIMEOUT HANDLER -- WORKER %s --] No activity detected for %d minutes. Closing connection." + Ansi.RESET + "\n", workerThread.getName(), maxDelay/60000);
                    state.activeUser.set(false);
                    break;
                }
                else{
                    System.out.printf(Ansi.YELLOW_BACKGROUND + "[--TIMEOUT HANDLER -- WORKER %s --] No activity detected from user %s for %d minutes. Logging out user and closing connection." + Ansi.RESET + "\n", workerThread.getName(), user, maxDelay/60000);
                    state.activeUser.set(false);
                    break;
                }
            }
        }
    }
}
