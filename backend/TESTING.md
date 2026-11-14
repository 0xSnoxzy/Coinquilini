# Test delle fondamenta

Responsabile delle fondamenta: **David (EsBru)**.

La suite `FoundationsTest` usa Spring Boot, JUnit 5, MockMvc e **PostgreSQL
reale**. Richiede Java 21 e Maven; `spring-boot-starter-test` è una dipendenza
con scope `test` e non modifica le dipendenze dell'applicazione in produzione.

Ogni esecuzione crea uno schema `foundation_test_<UUID>` nuovo. Tutte le
connessioni applicative usano esclusivamente quello schema tramite
`currentSchema`, senza fallback a `public`. Al termine viene eliminato solo
lo schema generato dalla suite, mai il database o gli schemi preesistenti.
Il ruolo PostgreSQL di test deve poter creare schemi nel database scelto.
La suite non legge le credenziali del database applicativo.

## Eseguire i test

Con un'istanza PostgreSQL di test già avviata, dalla cartella `backend`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/postgres?connectTimeout=5'
$env:TEST_DATABASE_USER = 'postgres'
$env:TEST_DATABASE_PASSWORD = ''
mvn --batch-mode --no-transfer-progress test
```

Adeguare `JAVA_HOME` alla propria installazione. Le tre variabili
`TEST_DATABASE_*` sono opzionali se si usano esattamente i valori dell'esempio.
Se PostgreSQL non risponde, la suite fallisce: non viene saltata e non ripiega
su un database in memoria. I report sono in `backend/target/surefire-reports`.

## Istanza PostgreSQL locale separata su Windows

Per usare i binari PostgreSQL installati senza avviare il servizio del database
applicativo, dalla cartella `backend`:

```powershell
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$pgData = Join-Path (Get-Location).Path 'target\postgres-foundations-tests'

# Solo la prima volta, su una cartella nuova:
if (-not (Test-Path -LiteralPath $pgData)) {
    & "$pgBin\initdb.exe" -D $pgData -U postgres -A trust -E UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'initdb fallito' }
}

& "$pgBin\pg_ctl.exe" -D $pgData -l "$pgData\server.log" -o '-h 127.0.0.1 -p 15432' -w start
if ($LASTEXITCODE -ne 0) { throw 'Avvio PostgreSQL di test fallito' }

try {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
    mvn --batch-mode --no-transfer-progress test
    if ($LASTEXITCODE -ne 0) { throw 'Test falliti' }
} finally {
    & "$pgBin\pg_ctl.exe" -D $pgData -m fast -w stop
}
```

Il cluster è dedicato ai test, ascolta solo su loopback e usa autenticazione
locale `trust`. La porta 15432 deve essere libera. I file generati restano
sotto `target`, esclusa da Git; il cluster viene fermato senza cancellarne
la cartella. Se si erano impostate variabili `TEST_DATABASE_*`, devono puntare
a questa istanza oppure vanno rimosse dalla sessione prima dell'esempio.

## Copertura ed esito

Il 14 novembre 2026 la suite ha completato **28 test, 0 fallimenti, 0 errori,
0 saltati**, usando Java 21.0.3 e PostgreSQL 18. Copre:

- riapplicazione dello schema e conservazione dei dati utente già inseriti;
- inserimenti con ID generati, letture, valori predefiniti e codici normalizzati;
- membri attuali/ex, separazione tra case e vista senza password;
- vincoli su codice invito, ruoli, chiavi esterne e coppia utente/casa;
- una sola appartenenza attiva per utente, anche con due transazioni concorrenti;
- commit, rollback per errori SQL/runtime, rollback delle modifiche `UserDAO`
  e partecipazione delle transazioni annidate alla transazione esterna;
- compatibilità dei controller esistenti per registrazione, login, sessione,
  aggiornamento del profilo e logout.

Il test dei controller in `FoundationsTest` è una verifica di regressione.
La copertura specifica dell'autenticazione è nella suite descritta di seguito.

## Test dell'autenticazione

Responsabile dell'autenticazione: **Antonio (snoxzy / 0xSnoxzy)**.

`AuthIntegrationTest` usa MockMvc, Spring Boot e i DAO JDBC reali. Crea uno
schema `auth_test_<UUID>` nella stessa istanza PostgreSQL separata usata dalla
suite delle fondamenta; al termine elimina esclusivamente il proprio schema.
Non richiede le credenziali del database applicativo e non ne modifica i dati.

Il comando `mvn --batch-mode --no-transfer-progress test` esegue tutte le
suite disponibili. Per eseguire solo quella dell'autenticazione:

```powershell
mvn --batch-mode --no-transfer-progress '-Dtest=AuthIntegrationTest' test
```

Le due suite iniziali, verificate il 14 novembre 2026 con Java 21.0.3 e
PostgreSQL 18, comprendono **61 test**:
33 di autenticazione e 28 delle fondamenta, con **0 fallimenti, 0 errori e
0 saltati**. La suite dell'autenticazione verifica:

- hash BCrypt persistito e assenza di password/hash nelle risposte e nei modelli JSON;
- normalizzazione email, duplicati e due registrazioni concorrenti;
- rifiuto di password errate, in chiaro o hash malformati e account disattivati;
- rinnovo dell'ID sessione e conservazione del solo `userId`;
- rifiuto delle vecchie sessioni, utenti inesistenti o disattivati dopo il login;
- rilettura dell'utente dal database e logout ripetuto;
- aggiornamento parziale del profilo, validazioni e protezione di ruolo, email e password;
- significatività degli spazi nella password e limite di 72 byte anche con Unicode;
- richieste CORS con credenziali dalle origini Angular esistenti.

MockMvc verifica gli endpoint senza avviare un browser. Il collaudo manuale
delle pagine Angular e del cookie nel browser rimane una verifica aggiuntiva;
vedi [documentazione dell'autenticazione](../docs/04-AUTENTICAZIONE.md).

## Test di creazione casa, ingresso e snapshot

`HouseIntegrationTest` usa uno schema PostgreSQL isolato `house_test_<UUID>`
con le stesse variabili `TEST_DATABASE_*`. Copre creazione atomica casa/OWNER,
join normalizzato, utenti disattivati, inviti non validi/case archiviate,
riattivazione di una membership storica, isolamento multi-casa, snapshot senza
credenziali, rollback e collisioni del codice. Verifica anche due creazioni
e due ingressi concorrenti per lo stesso utente.

Il 14 novembre 2026 la suite ha completato **26 test passati**. Con le due
suite precedenti il totale corrente è **87 test, 0 fallimenti, 0 errori,
0 saltati**, su Java 21.0.3 e PostgreSQL 18.

```powershell
mvn --batch-mode --no-transfer-progress '-Dtest=HouseIntegrationTest' test
```

La prova manuale dal browser ha verificato creazione, ingresso del secondo
utente, lista membri, ricaricamento e aggiornamento del profilo.
Limiti dello snapshot iniziale e passaggi riproducibili sono in
[Casa, inviti e snapshot](../docs/07-CASA-INVITI-SNAPSHOT.md).

Per installazione e collaudo sul PC di David usare il
[Registro del gruppo](../docs/00-STATO-GRUPPO.md), che mantiene anche i prossimi
task di ogni membro. L'esito del collaudo su quel computer deve essere
registrato separatamente dalle verifiche locali già concluse.
