# CROSS - an exChange oRder bOokS Service

[![Java](https://img.shields.io/badge/Java-11+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Academic-green.svg)]()

Un sistema di **order book** per exchange centralizzato di criptovalute, implementato come progetto per il corso di **Reti e Laboratorio - Modulo Laboratorio 3** (A.A. 2024/25).

---

## 📋 Indice

- [Descrizione](#-descrizione)
- [Funzionalità](#-funzionalità)
- [Architettura](#-architettura)
- [Requisiti](#-requisiti)
- [Installazione](#-installazione)
- [Utilizzo](#-utilizzo)
- [Struttura del Progetto](#-struttura-del-progetto)
- [Tecnologie Utilizzate](#-tecnologie-utilizzate)
- [Autore](#-autore)

---

## 🎯 Descrizione

**CROSS** è un servizio di gestione dell'order book che simula il funzionamento di un exchange centralizzato di criptovalute. Il sistema permette di:

- Gestire ordini di acquisto e vendita di Bitcoin (BTC) in USD
- Implementare un algoritmo di matching con priorità prezzo/tempo
- Mantenere lo stato persistente degli ordini e degli utenti
- Notificare in tempo reale l'esecuzione degli ordini

Il progetto è stato sviluppato seguendo le specifiche del documento **"Progetto LAB2425 - Versione 1.3"** per studenti del **nuovo ordinamento**.

---

## ✨ Funzionalità

### Gestione Utenti
- ✅ Registrazione e autenticazione
- ✅ Aggiornamento credenziali
- ✅ Login/Logout con gestione sessioni
- ✅ Disconnessione automatica per inattività

### Tipi di Ordini
- 📊 **Limit Order**: ordini con prezzo limite
- ⚡ **Market Order**: esecuzione immediata al miglior prezzo
- 🛑 **Stop Order**: ordini condizionali attivati al raggiungimento di un prezzo

### Order Book
- 🔄 Matching automatico ordini (time/price priority)
- 💾 Persistenza su file JSON
- 📈 Storico prezzi con statistiche giornaliere
- 📊 Visualizzazione stato corrente dell'order book

### Notifiche
- 🔔 Notifiche UDP in tempo reale sull'esecuzione degli ordini
- 📨 Best-effort delivery

---

## 🏗️ Architettura

### Architettura Client-Server

```
┌─────────────────┐         TCP          ┌─────────────────┐
│                 │◄─────────────────────►│                 │
│     Client      │                       │     Server      │
│                 │         UDP           │                 │
│  (CLI + GUI)    │◄─────────────────────►│   (Thread       │
│                 │     (Notifiche)       │     Pool)       │
└─────────────────┘                       └─────────────────┘
```

### Componenti Principali

#### Server
- **ServerMain**: Accetta connessioni e gestisce il ciclo di vita
- **Worker** (thread pool): Un thread per ogni client connesso
- **TimeoutHandler**: Monitora l'inattività dei client
- **OrderBook**: Gestisce la logica di matching degli ordini

#### Client
- **ClientMain**: Interfaccia CLI per l'utente
- **Receiver (TCP)**: Riceve risposte dal server
- **ReceiverUDP**: Riceve notifiche di trade
- **Printer**: Gestisce l'output asincrono

### Strutture Dati
- `ConcurrentSkipListMap`: Order book (ask/bid map)
- `ConcurrentHashMap`: Gestione utenti
- `ConcurrentLinkedQueue`: Code FIFO per priority time
- `BlockingQueue`: Comunicazione tra thread

---

## 📦 Requisiti

- **Java JDK**: 8 o superiore
- **Maven**: 3.6 o superiore
- **Sistema Operativo**: Linux, macOS, Windows

---

## 🚀 Installazione

### 1. Clona il repository
```bash
git clone https://github.com/tvicxx/cross
cd cross
```

### 2. Compila il progetto con Maven
```bash
mvn clean package
```

Questo comando genera due JAR eseguibili nella cartella `target/`:
- `cross-server.jar`
- `cross-client.jar`

---

## 💻 Utilizzo

### Configurazione

Prima dell'esecuzione, verifica i file di configurazione in `src/main/java/`:

**`server.properties`**
```properties
TCPport:1111
UDPport:2222
maxDelay:300000
hostname:localhost
userMapPath:src/main/java/JsonFile/userMap.json
orderBookPath:src/main/java/JsonFile/orderBook.json
storicoOrdiniPath:src/main/java/JsonFile/storicoOrdini.json
```

**`client.properties`**
```properties
TCPport:1111
hostname:localhost
```

### Avvio del Server

```bash
java -jar target/cross-server.jar
```

Output:
```
[--ServerMain--] Loading configuration...
[--ServerMain--] UserMap loaded successfully!
[--ServerMain--] OrderBook loaded successfully!
[--ServerMain--] Server is starting on port 1111...
```

### Avvio del Client

```bash
java -jar target/cross-client.jar
```

### Comandi Disponibili

```bash
# Gestione Account
register(username, password)           # Registra nuovo utente
updateCredentials(user, oldPsw, newPsw) # Aggiorna password
login(username, password)              # Accedi al servizio
logout()                               # Disconnetti

# Ordini
insertLimitOrder(tipo, size, price)    # Ordine limite
insertMarketOrder(tipo, size)          # Ordine a mercato
insertStopOrder(tipo, size, stopPrice) # Ordine stop
cancelOrder(orderId)                   # Cancella ordine

# Informazioni
getPriceHistory(MMYYYY)                # Storico prezzi
showOrderBook()                        # Visualizza OrderBook
help()                                 # Mostra aiuto
```

### Esempio Sessione Completa

```bash
>>> register(alice, mypassword)
[Client] Operation successful!

>>> login(alice, mypassword)
[Client] Operation successful!

>>> insertLimitOrder(bid, 1000, 58000000)
[Client] Order inserted successfully! Order ID: 1

>>> insertMarketOrder(ask, 500)
[Client] Order inserted successfully! Order ID: 2
[Client] Trade Notification: Order ID 1 executed for size 500 at price 58000000

>>> getPriceHistory(012025)
[Mostra storico gennaio 2025]

>>> logout()
Grazie per aver usato il nostro servizio di trading!
```

### Note sui Formati

- **size**: Millesimi di BTC (1000 = 1 BTC)
- **price**: Millesimi di USD (58000000 = 58000 USD)
- **tipo**: `ask` (vendita) o `bid` (acquisto)
- **mese**: Formato `MMYYYY` (es: 012025 per gennaio 2025)

---

## 📁 Struttura del Progetto

```
cross/
│
├── pom.xml                         # Configurazione Maven
├── README.md                       # Questo file
│
├── src/main/java/
│   ├── client.properties           # Config client
│   ├── server.properties           # Config server
│   │
│   ├── Eseguibili/
│   │   ├── Main/
│   │   │   ├── ClientMain.java    # Entry point client
│   │   │   └── ServerMain.java    # Entry point server
│   │   ├── Client/
│   │   │   ├── Printer.java       # Output asincrono
│   │   │   ├── Receiver.java      # Ricezione TCP
│   │   │   └── ReceiverUDP.java   # Ricezione UDP
│   │   └── Server/
│   │       ├── Worker.java         # Handler connessioni
│   │       ├── TimeoutHandler.java # Gestione inattività
│   │       └── Tupla.java          # Dati utente
│   │
│   ├── OrderBook/
│   │   ├── OrderBook.java          # Logica order book
│   │   ├── OrderValue.java         # Valore ordine
│   │   ├── UserValue.java          # Info utente ordine
│   │   ├── StopValue.java          # Stop order
│   │   ├── TradeNotifyUDP.java     # Notifica UDP
│   │   └── DayPriceData.java       # Statistiche giornaliere
│   │
│   ├── GsonClasses/
│   │   ├── GsonMess.java           # Messaggio generico
│   │   ├── Commands/               # Comandi client
│   │   └── Responses/              # Risposte server
│   │
│   ├── JsonFile/
│   │   ├── userMap.json            # Database utenti
│   │   ├── orderBook.json          # Stato order book
│   │   └── storicoOrdini.json      # Storico trades
│   │
│   └── Varie/
│       └── Ansi.java               # Colori terminale
│
└── target/                         # Output compilazione
    ├── cross-server.jar
    └── cross-client.jar
```

---

## 🛠️ Tecnologie Utilizzate

- **Java 11**: Linguaggio di programmazione
- **Maven**: Build automation e dependency management
- **Gson 2.10.1**: Serializzazione/deserializzazione JSON
- **Java NIO**: Networking (Socket TCP/UDP)
- **Concurrent Collections**: Thread-safety
- **ExecutorService**: Thread pooling

### Caratteristiche Tecniche

- ✅ Architettura multi-threaded con thread pool
- ✅ Sincronizzazione con `synchronized`, strutture concurrent e `AtomicBoolean`
- ✅ Persistenza dati in formato JSON
- ✅ Comunicazione TCP per comandi, UDP per notifiche
- ✅ Pattern Producer-Consumer per output asincrono
- ✅ Gestione graceful shutdown

---

## 📊 Algoritmo di Matching

Il sistema implementa l'algoritmo **time/price priority**:

1. **Price Priority**: Gli ordini con prezzo migliore vengono eseguiti per primi
   - Ask: prezzi più bassi hanno priorità
   - Bid: prezzi più alti hanno priorità

2. **Time Priority**: A parità di prezzo, gli ordini più vecchi vengono eseguiti per primi

3. **Partial Fill**: Un ordine può essere soddisfatto da ordini di più utenti

### Esempio di Matching

```
Order Book Iniziale:
ASK: [100 @ 58000, 200 @ 58500]
BID: [150 @ 57000]

Nuovo ordine: BID 120 @ 58500

Matching:
- Prende 100 dal primo ask @ 58000 (esaurito)
- Prende 20 dal secondo ask @ 58500 (rimangono 180)
- Ordine completamente eseguito

Order Book Finale:
ASK: [180 @ 58500]
BID: [150 @ 57000]
```

---

## 🔒 Sicurezza e Gestione Errori

- ✅ Validazione input (username alfanumerico, password non vuota)
- ✅ Controllo ownership degli ordini (un utente può cancellare solo i propri)
- ✅ Prevenzione self-trading (gli ordini dello stesso utente non matchano tra loro)
- ✅ Timeout di inattività (5 minuti default)
- ✅ Gestione disconnessioni improvvise
- ✅ Atomic operations su dati condivisi

---

## 📝 Testing

### Test Consigliati

```bash
# Test 1: Registrazione e Login
>>> register(user1, pass1)
>>> login(user1, pass1)

# Test 2: Limit Orders
>>> insertLimitOrder(ask, 1000, 60000000)  # Vende 1 BTC @ 60k USD
>>> insertLimitOrder(bid, 500, 59000000)   # Compra 0.5 BTC @ 59k USD

# Test 3: Market Order (dovrebbe matchare con limit orders)
>>> insertMarketOrder(bid, 1000)

# Test 4: Stop Order
>>> insertStopOrder(ask, 1000, 55000000)   # Vende se prezzo scende a 55k

# Test 5: Cancellazione
>>> cancelOrder(1)

# Test 6: Storico
>>> getPriceHistory(012025)
```

### Scenario Multi-Client

Avvia più client simultaneamente per testare:
- Matching tra ordini di utenti diversi
- Notifiche UDP concorrenti
- Gestione connessioni multiple

---

## 🐛 Troubleshooting

### Porta già in uso
```bash
# Modifica le porte nei file .properties
TCPport:2222
UDPport:3333
```

### File JSON non trovati
```bash
# Verifica che esistano in src/main/java/JsonFile/
ls -la src/main/java/JsonFile/
```

### Errore compilazione Maven
```bash
mvn clean
mvn package -X  # Debug mode
```

### Client non riceve notifiche UDP
- Verifica che il firewall non blocchi la porta UDP
- Controlla che `UDPport` sia la stessa in entrambi i properties

---

## 🎓 Contesto Accademico

**Corso**: Reti e Laboratorio - Modulo Laboratorio 3  
**Anno Accademico**: 2024/25  
**Università**: Università di Pisa  
**Docente**: Laura Maria Emilia Ricci 

---

## 👤 Autore

**Tommaso Vicarelli**  
Matricola: 638912  

📧 Email: t.vicarelli@studenti.unipi.it
🔗 GitHub: @tvicxx (https://github.com/tvicxx)  
💼 LinkedIn: https://www.linkedin.com/in/tommaso-vicarelli/

---

## 📜 Licenza

Questo progetto è stato sviluppato per scopi accademici. Il codice è disponibile per consultazione e studio.

---

## 🙏 Ringraziamenti

Ringraziamenti ai docenti del corso di Reti e Laboratorio per le specifiche del progetto e il supporto durante lo sviluppo.

---

## 📌 Note Finali

- ⚠️ Questo progetto è stato sviluppato per scopi **esclusivamente didattici**
- 💡 Non è ottimizzato per uso in produzione
- 🔄 Gli Stop Orders non sono persistenti (solo in memoria)
- 📊 Lo storico ordini può crescere significativamente nel tempo

---

**Ultima modifica**: Novembre 2025  
**Versione**: 1.0.0

---

<div align="center">

**⭐ Se questo progetto ti è stato utile, lascia una stella! ⭐**

Made with ☕ and 💻 by Tommaso Vicarelli

</div>
