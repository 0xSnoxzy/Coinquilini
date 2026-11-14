# Autenticazione reale e configurazione locale

Il backend richiede Java 21, Maven e PostgreSQL. Le API di autenticazione e
profilo sono reali; gli altri flussi del frontend possono ancora usare i dati
dimostrativi. Non sono state modificate la grafica o le chiamate Angular.

## Credenziali PostgreSQL

`application.properties` legge queste variabili d'ambiente:

| Variabile | Valore |
| --- | --- |
| `DB_URL` | Facoltativa; predefinito `jdbc:postgresql://localhost:5432/coinquilini` |
| `DB_USERNAME` | Obbligatoria; utente PostgreSQL locale |
| `DB_PASSWORD` | Obbligatoria; password PostgreSQL locale |

Esempio PowerShell dalla cartella `backend`, senza scrivere password nei file
o nella cronologia dei comandi:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
$dbCredential = Get-Credential -Message 'Credenziali PostgreSQL locali'
$env:DB_USERNAME = $dbCredential.UserName
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
$env:DB_URL = 'jdbc:postgresql://localhost:5432/coinquilini'
mvn spring-boot:run
```

Le variabili valgono per quel terminale e per il processo avviato da esso.
Per avviare dall'IDE, configurarle nell'ambiente della run configuration senza
salvarne i valori nel repository. Il database deve già esistere. Non occorre
cancellare dati o ricreare tabelle.

La dipendenza `spring-security-crypto` è gestita dalla versione Spring Boot
del progetto; Maven la scarica alla prima compilazione. Non viene aggiunto lo
starter Spring Security e non vengono introdotte pagine di login automatiche.

## Comportamento delle API

- `POST /api/auth/register`: nome, email e password; restituisce `201` e i dati
  pubblici dell'utente. L'email viene ripulita dagli spazi esterni e convertita
  in minuscolo con `Locale.ROOT`. I duplicati, incluse registrazioni concorrenti,
  restituiscono `409` con `{"error":"..."}`.
- `POST /api/auth/login`: ricerca per sola email normalizzata e verifica con
  `BCryptPasswordEncoder.matches`. Credenziali errate o vecchie password in
  chiaro restituiscono `401`; un account disattivato con credenziali corrette
  restituisce `403`.
- Registrazione e login rinnovano l'ID sessione e conservano come unico
  attributo applicativo `userId`, di tipo `Long`.
- `GET /api/auth/me` e `PUT /api/profile/save` usano `AuthService.currentUser`,
  che rilegge l'utente dal database a ogni richiesta. Sessioni assenti o non
  valide e utenti inesistenti restituiscono `401`; account disattivati `403`.
  Le sessioni con utente inesistente o disattivato vengono invalidate.
- Il profilo consente di aggiornare nome, colore e foto. Campi non inviati
  rimangono invariati; nome vuoto o oltre 100 caratteri e colore diverso da
  `#RRGGBB` restituiscono `400`. Email, ruolo, stato e password non sono modificabili
  tramite questa API.
- `POST /api/auth/logout` invalida la sessione se presente, anche dopo logout
  ripetuti. Le password non vengono mai ripulite dagli spazi; il limite è
  72 byte UTF-8 per evitare il troncamento BCrypt.

Le risposte utente contengono solo `id`, `name`, `email`, `role`, `active`,
`color`, `photo`. Password e hash sono esclusi sia dal DTO delle risposte sia
dalla serializzazione Jackson di `User`. I nuovi endpoint autenticati dovranno
riutilizzare `AuthService.currentUser` anziché leggere oggetti utente in sessione.

## Account di prova preesistenti

Lo schema resta invariato: la colonna `app_user.password` contiene ora l'hash
BCrypt, non la password in chiaro. Non esiste alcun accesso alternativo o
conversione automatica al login. Le vecchie sessioni contenenti l'oggetto
`user` sono rifiutate: è necessario accedere di nuovo.

Per proseguire con gli account dimostrativi ci sono due possibilità:

1. Registrare un nuovo utente con email diversa: gli account e i dati precedenti
   restano intatti, ma il nuovo utente avrà un altro identificativo.
2. Per preservare identità, appartenenze e dati collegati, scegliere una nuova
   password di prova e generarne **localmente** l'hash con
   `new BCryptPasswordEncoder().encode(password)`, usando la stessa dipendenza
   del backend. Un amministratore del database può sostituire esclusivamente
   `app_user.password` dell'ID interessato con quell'hash, in una transazione
   verificata prima del commit. Non modificare ID, ruoli o altre tabelle.

Per generare l'hash si può usare JShell dopo la compilazione. Dalla cartella
`backend` preparare il classpath:

```powershell
mvn dependency:build-classpath '-Dmdep.outputFile=target/runtime-classpath.txt'
$authClasspath = (Get-Content target/runtime-classpath.txt -Raw).Trim()
& "$env:JAVA_HOME/bin/jshell.exe" --class-path $authClasspath
```

Dentro JShell, con una password scelta solo per l'account di prova:

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
new BCryptPasswordEncoder().encode("SOSTITUIRE_CON_PASSWORD_DI_PROVA");
/exit
```

Non versionare password, hash generati o script contenenti questi valori.
Prima di una conversione manuale verificare eventuali email preesistenti
equivalenti dopo trim/minuscolo: risolverle esplicitamente senza cancellare
account o collegamenti. Questa implementazione non modifica i dati già presenti.

## Test automatici e verifiche manuali aggiuntive

La suite `AuthIntegrationTest` verifica le API con MockMvc e i DAO su PostgreSQL
reale in uno schema isolato. Il 14 novembre 2026, con Java 21.0.3 e PostgreSQL
18, sono passati **33 test di autenticazione** e **28 test delle fondamenta**:
61 totali, nessun fallimento, errore o test saltato. Le istruzioni per ripetere
le suite e avviare l'istanza separata sono in [backend/TESTING.md](../backend/TESTING.md).

La suite copre hashing, normalizzazione email, registrazioni concorrenti,
account disattivati, sessioni rinnovate e invalidate, profilo, esclusione delle
credenziali dal JSON, CORS e limite BCrypt in byte UTF-8. Restano utili queste
verifiche manuali dal browser:

1. Registrazione con email mista maiuscole/spazi: verificare email normalizzata,
   hash BCrypt nel database e risposta priva di credenziali.
2. Registrazione duplicata, anche concorrente: una sola creazione, errore `409`.
3. Login con password corretta/errata e con vecchio valore in chiaro: solo
   l'hash valido consente l'accesso; verificare anche il limite UTF-8 di 72 byte.
4. Confrontare il cookie `JSESSIONID` prima/dopo login e registrazione;
   verificare che la sessione contenga soltanto `userId`.
5. Controllare `/me`, aggiornamento parziale del profilo e logout ripetuto.
6. Disattivare un account di prova dopo il login: `/me` e salvataggio profilo
   devono rifiutarlo e invalidare la sessione; su un ambiente isolato verificare
   anche il ramo dell'utente non più esistente senza cancellare dati condivisi.
7. Verificare avvio con variabili DB presenti e errore di configurazione quando
   mancano le credenziali; controllare il flusso dalle pagine Angular esistenti.

Per compilare senza compilare o eseguire test:

```powershell
mvn '-Dmaven.test.skip=true' compile
```

Riferimento API: [Spring Security Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
