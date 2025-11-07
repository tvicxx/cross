package Eseguibili.Server;

//classe Tupla per memorizzare la password e lo stato di login di un utente
public class Tupla{
    public String password;
    public Boolean isLogged;

    public Tupla(String password,Boolean isLogged){
        this.password = password;
        this.isLogged = isLogged;
    }

    public String getPassword(){
        return this.password;
    }
    public Boolean getIsLogged(){
        return this.isLogged;
    }
    public void setIsLogged(Boolean isLogged){
        this.isLogged = isLogged;
    }
}