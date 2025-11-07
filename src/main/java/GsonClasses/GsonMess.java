package GsonClasses;

//classe generica di ricezione di un messaggio dal client
public class GsonMess <T extends Values> {
    public String operation;
    //generics per i valori in base all'operazione richiesta
    public T values = null;

    public GsonMess(String operation, T values){
        this.operation = operation;
        this.values = values;
    }

    public String toString(){
        String valuesStr = (this.values == null) ? "null" : this.values.toString();
        return "{ operation = " + this.operation + ", values = " + valuesStr + "}";
    }
}