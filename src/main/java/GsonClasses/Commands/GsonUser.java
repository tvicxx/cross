package GsonClasses.Commands;

import GsonClasses.Values;

//classe che rappresenta un utente in formato Gson, usata per il login o la registrazione
public class GsonUser extends Values {
    public String username;
    public String password;

    public GsonUser(String username, String password){
        this.username = username;
        this.password = password;
    }

    public String toString(){
        return "{ username = " + this.username + ", password = " + this.password + "}";
    }

    public String getUsername(){
        return this.username;
    }

    public String getPassword(){
        return this.password;
    }
}
