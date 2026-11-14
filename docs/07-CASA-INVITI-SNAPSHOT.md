# Creazione casa, ingresso con invito e snapshot iniziale

Stato del 14 novembre 2026: implementato sul branch
`antonio/casa-inviti-snapshot`, a partire dal main `301fde5`.
Backend nel commit `34617fb`, frontend nel commit `5e8e6c0`.
La prova sul PC di David precede il merge: seguire e aggiornare il
[Registro del gruppo](00-STATO-GRUPPO.md).

## Funzioni disponibili

- Creazione casa con nome ed emoji, codice invito casuale di sei caratteri
  e assegnazione del creatore come OWNER.
- Inserimento di casa e appartenenza nella stessa transazione JDBC.
- Ingresso tramite codice, ignorando maiuscole/minuscole e spazi.
- Rifiuto di inviti inesistenti, case archiviate e seconda appartenenza attiva.
- Rientro di un ex membro riattivando la stessa riga membership come MEMBER.
- Caricamento reale di utente, casa e membri correnti/ex nello snapshot.
- Persistenza di identità e casa al ricaricamento e dopo un nuovo login.
- Aggiornamento del profilo e dei suoi dati nella lista membri.
- Modalità demo separata: le sue modifiche non inviano scritture alle API.

La grafica e i CSS restano invariati. Cambiano soltanto il collegamento dei
dati e le etichette necessarie: in modalità reale i pulsanti sono **Aggiorna
dati** ed **Esci**; i riferimenti all'anteprima restano nella modalità demo.
`schema.sql`, `models.ts` e le dipendenze Maven non sono stati modificati.

## API

| Metodo e percorso | Corpo / risposta |
| --- | --- |
| `POST /api/houses/create` | `{ "name": "Casa", "emoji": "🏠" }` → `201 { "ok": true }` |
| `POST /api/houses/join` | `{ "code": "ABC123" }` → `200 { "ok": true }` |
| `GET /api/snapshot` | `Snapshot` compatibile con `frontend/src/app/core/models.ts` |

Tutte richiedono la sessione. Utenti inesistenti o senza sessione ricevono
401; account disattivati 403 con invalidazione della sessione. I conflitti di
appartenenza ricevono 409, dati non validi 400, invito inesistente 404 e
casa archiviata 403. Gli errori applicativi hanno forma `{ "error": "..." }`.

Il nome della casa viene ripulito e accetta al massimo 100 caratteri; emoji
assente/vuota usa 🏠. Il codice è generato dal server, non accettato dal client.
I dati dell'utente, della casa attiva e dei membri sono ricavati dalla sessione
e dal database. Un parametro `houseId` nello snapshot non cambia la casa letta.
Le risposte usano la vista condivisa `PublicUser`, senza password o hash.

## Transazioni e concorrenza

`HouseService` usa `JdbcTransactions`. Il recupero centralizzato
`AuthService.currentUserForUpdate` verifica l'account e blocca la riga utente
per la durata della transazione. Due richieste di creazione/ingresso dello
stesso utente vengono serializzate. Il vincolo UNIQUE parziale già presente
rimane la protezione aggiuntiva nel database.

Il join blocca anche la casa letta per codice e ne controlla lo stato prima
di aggiungere il membro. Per una membership storica esistente viene riattivata
la stessa riga e il ruolo diventa MEMBER, senza recuperare un precedente OWNER.

Se l'inserimento dell'OWNER fallisce, la nuova casa viene annullata dal rollback.
Se il codice invito collide, la creazione riparte in una nuova transazione
(massimo cinque tentativi); non prosegue nella transazione SQL già fallita.

## Limite dello snapshot di questo traguardo

Sono reali soltanto `user`, `house` e `members`. Gli altri array del contratto
sono vuoti e `myBalance` è zero perché i relativi moduli non sono ancora
implementati. Questi valori non costituiscono un calcolo economico.

Spese, lista, mansioni, avvisi e admin rimangono da implementare. In modalità
reale le loro mutazioni, insieme a modifica casa, rigenerazione invito,
trasferimento OWNER, rimozione e uscita, restituiscono nel frontend un messaggio
di funzione non ancora collegata, senza simulare il successo.
La lettura non è aggiornata in tempo reale via WebSocket: un membro vede i
nuovi ingressi premendo **Aggiorna dati** o ricaricando la pagina.

## Verifiche eseguite

Con Java 21.0.3 e PostgreSQL 18, **87 test passati**, zero fallimenti/errori/saltati:

- 33 test autenticazione;
- 28 test fondamenta JDBC;
- 26 test nuovi di casa e snapshot, inclusi rollback, collisione del codice,
  creazioni/join concorrenti, isolamento multi-casa, storico e account disattivati.

Comandi dalla cartella backend con l'istanza PostgreSQL di test avviata:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
$env:TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/postgres?connectTimeout=5'
$env:TEST_DATABASE_USER = 'postgres'
$env:TEST_DATABASE_PASSWORD = ''
mvn --batch-mode --no-transfer-progress test
```

Superate anche build Angular production e creazione/avvio del JAR Spring Boot.
Nel browser sono stati creati due account dedicati al collaudo: il primo ha
creato una casa, il secondo è entrato con il codice minuscolo. Entrambi hanno
visto gli stessi due membri, con ruoli OWNER/MEMBER, anche dopo il ricaricamento.
Verificati anche salvataggio profilo e Aggiorna dati senza ripristino dei mock.

Il collaudo usa il database separato sulla porta 15432. I dati applicativi
preesistenti non sono stati cancellati o convertiti; i test automatici usano
schemi temporanei differenti dallo schema della prova personale.

## Prova personale e prossimi commit

L'ambiente di prova è disponibile su http://localhost:3000, con backend su
localhost:8080. Segui [la guida pratica](06-PROVA-PERSONALE.md): crea la tua
casa, apri Impostazioni e condividi il codice con un secondo utente in un altro
browser o in una finestra privata. Ricarica entrambe le pagine per aggiornare
la lista membri. Gli account già registrati possono accedere senza registrarsi
nuovamente; dopo il riavvio del backend serve effettuare di nuovo il login.

Commit del blocco da revisionare:

1. `34617fb` — `feat(houses): creazione inviti e snapshot con test PostgreSQL`
2. `5e8e6c0` — `feat(store): collega casa e membri alle API reali`
3. `docs: registro condiviso e collaudo sul PC di David`

Lo snapshot dipende dalla vista utente condivisa e i test casa lo usano:
tenere questi collegamenti compilabili se si decide di separare i commit.
Restano esclusi da questo traguardo i successivi permessi OWNER, uscita con
saldo zero e tutti i moduli di dominio elencati sopra.
