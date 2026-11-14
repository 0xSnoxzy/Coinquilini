# Provare personalmente il lavoro già completato

Ambiente aggiornato il 14 novembre 2026 con il flusso casa/inviti del branch
`codex/casa-inviti-snapshot`, basato su main `301fde5`.
Frontend: http://localhost:3000 — backend: http://localhost:8080.
I servizi sono stati lasciati avviati per la prova personale; non ripartono
automaticamente dopo un riavvio del computer.

Questa guida descrive l'ambiente locale preparato sul PC di Antonio.
Per installare e provare sul PC di David usare invece la checklist del
[Registro del gruppo](00-STATO-GRUPPO.md), con le credenziali e il database
locali di quel PC. Lo schema di prova personale indicato sotto è specifico
del PC di Antonio.

## Cosa stai provando

Il percorso reale è: pagina Angular → Controller → Service → DAO JDBC
→ PostgreSQL. Autenticazione, profilo, creazione casa, ingresso con invito e
snapshot usano questo percorso. I dati del tuo account di prova sono persistenti sul database locale
separato; nessun account del database applicativo viene cambiato.

BCrypt protegge la password salvando un hash con salt, non la password stessa.
La sessione HTTP mantiene invece l'accesso dopo il login: il browser conserva
un cookie `JSESSIONID`, mentre il server conserva il solo `userId` come attributo
applicativo e rilegge l'utente dal database.

## Prova dalla pagina

1. Apri http://localhost:3000 e scegli **Prima volta? Registrati**.
2. Inserisci un nome riconoscibile, un'email di prova, ad esempio
   `antonio-personale@example.invalid`, e una password scelta solo per questa
   prova, di almeno 8 caratteri. Non serve una casella email reale.
3. Premi **Registrati**: devi vedere il tuo nome e la schermata per creare
   una casa o entrare con invito. L'account viene scritto realmente nel DB.
4. Premi **F5**: il tuo nome deve rimanere, perché `/api/auth/me` recupera la
   sessione dal server. Non è necessario reinserire la password.
5. Premi **Esci**: esegue davvero il logout. In modalità demo il pulsante
   mantiene l'etichetta Cambia profilo di esempio.
6. Prova ad accedere con la tua email e una password sbagliata: devi leggere
   **Credenziali non valide**.
7. Accedi con la password corretta, anche scrivendo l'email in maiuscolo:
   devi ritrovare il tuo utente.
8. Dopo il logout prova a registrare di nuovo la stessa email: deve comparire
   l'errore di email già registrata.

Per questa prova usa i moduli reali di login/registrazione. I pulsanti con i
nomi Antonio/David/Elisa/Giovanna/Admin/Nuovo caricano ancora scenari fittizi.
In modalità reale **Aggiorna dati** rilegge lo snapshot dal database;
**Ripristina anteprima** compare solo quando usi i mock.

## Crea una casa ed entra con un secondo utente

1. Dopo il login inserisci il nome della casa e premi **Crea casa**.
2. Vedrai la bacheca con la tua casa e un membro: sei il responsabile OWNER.
3. Apri **Impostazioni**. Nella sezione **Invita un coinquilino** trovi il codice
   reale di sei caratteri; copialo.
4. In un altro browser oppure in una finestra privata apri lo stesso sito e
   registra un secondo utente con email diversa.
5. Nel campo **Codice d'invito** inserisci il codice e premi **Entra nella casa**.
   Maiuscole/minuscole e spazi non cambiano il risultato.
6. Apri Impostazioni: il secondo utente vede entrambi i membri e il proprio
   ruolo di coinquilino. Torna al primo browser e premi **Aggiorna dati** per
   vedere il nuovo membro.
7. Premi F5 in entrambi: nomi, casa e appartenenze devono rimanere gli stessi.
8. Dalle Impostazioni prova anche a salvare il tuo profilo e ricaricare.

Modifica casa, rigenerazione codice, cambio OWNER, uscita e rimozione sono
traguardi successivi: i relativi pulsanti oggi segnalano la funzione mancante.
Anche spese, lista, mansioni e avvisi sono ancora da collegare al database.

## Vedere BCrypt nel database con pgAdmin

Aggiungi in pgAdmin una connessione con questi valori:

| Campo | Valore |
| --- | --- |
| Nome a scelta | Coinquilini — prova personale |
| Host | `127.0.0.1` |
| Porta | `15432` |
| Maintenance database | `postgres` |
| Username | `postgres` |
| Password | Nessuna: questa istanza dedicata ai test usa trust su loopback. |

Espandi **Databases → postgres → Schemas →
auth_browser_bb2454e1e5224b679349a81d5e0a1472 → Tables → app_user**.
Se la struttura era già aperta, aggiorna l'albero di pgAdmin.

Dal Query Tool puoi eseguire questa sola lettura:

```sql
SELECT id, name, email, active,
       left(password, 7) AS prefisso_hash,
       length(password) AS lunghezza_hash
FROM auth_browser_bb2454e1e5224b679349a81d5e0a1472.app_user
ORDER BY id;
```

Vedrai il tuo account, l'email in minuscolo, un prefisso come `$2a$10$` e
lunghezza 60. Nella colonna esistente `password` è memorizzato l'hash, non il
testo che hai digitato. Gli utenti creati nel precedente collaudo possono
essere presenti nello stesso schema; non occorre cancellarli.

## Vedere la sessione nel browser

Apri gli strumenti sviluppatore del browser, la sezione dedicata a
Storage/Archiviazione o Application e poi i cookie di `http://localhost:3000`.
I cookie sono legati al dominio, non alla porta: quello inviato dal backend
localhost:8080 può essere visibile anche nella pagina localhost:3000.

Dopo registrazione/login deve esserci `JSESSIONID` con flag `HttpOnly`.
È un identificatore casuale della sessione: non contiene la tua password.
`HttpOnly` impedisce al JavaScript della pagina di leggere quel cookie.
Il rinnovo dell'ID all'accesso è coperto anche dai test automatici.

Puoi aprire http://localhost:8080/api/auth/me in un'altra scheda dello stesso
browser. Mentre sei autenticato mostra il tuo utente in JSON senza password
o hash. Dopo il logout, ricaricando quella scheda, restituisce HTTP 401.
La sessione resta in memoria nel backend: riavviare il backend richiede un
nuovo login, mentre l'account nel database rimane.

## Dove entrano DAO case/membri e transazioni

Una DAO è il componente Java che legge e scrive una tabella:

- `HouseDAOJDBC`: salva e cerca case nella tabella `house`.
- `MembershipDAOJDBC`: salva appartenenze e legge membri di una casa dalla
  tabella `membership` insieme ai dati pubblici di `app_user`.
- `JdbcTransactions`: raggruppa più operazioni DAO nella stessa transazione.

Esempio del prossimo flusso: creare una casa e assegnare il suo OWNER sono
due scritture che devono riuscire insieme. Se fallisce la seconda, il rollback
annulla anche la prima, così non resta una casa senza responsabile.

Ora **Crea casa ed Entra chiamano davvero le DAO**. In pgAdmin puoi vedere
le righe nelle tabelle `house` e `membership` dello stesso schema della tua
prova personale, e controllare il tuo ID, l'ID casa e il ruolo OWNER/MEMBER.
I test rimangono il modo per osservare anche gli errori e i rollback senza
alterare i dati della tua casa.

Per esercitare personalmente quelle classi puoi lanciare `FoundationsTest`
dall'IDE con Java 21, oppure dalla cartella `backend`:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
$env:TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/postgres?connectTimeout=5'
$env:TEST_DATABASE_USER = 'postgres'
$env:TEST_DATABASE_PASSWORD = ''
mvn --batch-mode --no-transfer-progress '-Dtest=FoundationsTest' test
```

I test scrivono tramite le DAO reali su schemi isolati generati dalla suite.
Questi tre metodi sono particolarmente utili da eseguire singolarmente o
seguire con il debugger:

- `houseRoundTripPreservesAllFieldsAndGeneratedId`: inserisce e rilegge una casa.
- `multiDaoTransactionCommitsAndAutocommitWorks`: conferma più scritture insieme.
- `runtimeFailureRollsBackUserUpdateAndNewHouseAndMembership`: provoca un
  errore e dimostra che le modifiche della transazione non restano salvate.

Per tutti gli 87 test usa `mvn --batch-mode --no-transfer-progress test`.
Non serve modificare i dati della tua prova personale: i test usano altri
schemi e ne fanno la pulizia autonomamente. Le istruzioni complete sono in
[TESTING.md](../backend/TESTING.md).

La pagina Impostazioni diventa disponibile dopo creazione o ingresso in casa.
Il salvataggio profilo e il suo caricamento dal database sono stati verificati
anche nel browser durante questo traguardo.

I processi avviati e i log sono annotati sotto `backend/target/prova-personale-*`.
Il codice verificato è nei commit `34617fb` (backend) e `5e8e6c0` (frontend).
Lo stato del collaudo sul PC di David e i prossimi task sono nel registro condiviso.
