package GsonClasses.Commands;

import GsonClasses.Values;

//classe che rappresenta una richiesta di aggiornamento delle credenziali in formato Gson
public class GsonUpdateCredentials extends Values {
    public String username;
    public String old_password;
    public String new_password;

    public GsonUpdateCredentials(String username, String old_password, String new_password){
        this.username = username;
        this.old_password = old_password;
        this.new_password = new_password;
    }

    public String toString(){
        return "{ username = " + this.username + ", old_password = " + this.old_password + ", new_password = " + this.new_password + "}";
    }

    public String getUsername(){
        return username;
    }
    public String getOldPassword(){
        return old_password;
    }
    public String getNewPassword(){
        return new_password;
    }
}
