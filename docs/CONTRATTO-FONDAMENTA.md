# Contratto delle fondamenta: case, appartenenze e transazioni

## Ambito e compatibilità

Backend Java 21, Spring Boot 3.3.4, PostgreSQL e DAO JDBC. Nessun controller o
collegamento frontend aggiunto. `app_user` resta invariata, inclusa la colonna
esistente `password`: questa attività non migra credenziali né modifica utenti.

`schema.sql` aggiunge solo tabelle e indici con `IF NOT EXISTS`, senza `DROP`,
`TRUNCATE`, cancellazioni o inserimenti demo. La configurazione esistente
`spring.sql.init.mode=always` applica lo schema al prossimo avvio. Non è uno
strumento di migrazione: se una tabella omonima esiste già con una struttura
diversa, `CREATE TABLE IF NOT EXISTS` non la corregge automaticamente.

## Colonne e modelli

### `house` → `House`

| Colonna PostgreSQL | Tipo | Proprietà Java/TypeScript | Vincoli/default |
| --- | --- | --- | --- |
| `id` | `SERIAL` | `id` | PK, generato dal DB |
| `name` | `VARCHAR(100)` | `name` | NOT NULL |
| `emoji` | `VARCHAR(20)` | `emoji` | NOT NULL, `🏠` |
| `photo` | `TEXT` | `photo` | NOT NULL, stringa vuota |
| `invite_code` | `VARCHAR(20)` | `inviteCode` | NOT NULL, UNIQUE, esattamente 6 caratteri ASCII `A-Z`/`0-9` |
| `archived` | `BOOLEAN` | `archived` | NOT NULL, `false` |

Il DAO normalizza il codice in inserimento e ricerca: rimuove gli spazi
riconosciuti da `\s` Java e converte in maiuscolo con `Locale.ROOT`.
Il CHECK SQL impone la forma canonica anche per scritture esterne al DAO;
insieme a UNIQUE impedisce duplicati con differenze di maiuscole/minuscole.
Il DAO non genera il codice: lo fornirà il futuro servizio applicativo.

### `membership` → `Membership`

| Colonna PostgreSQL | Tipo | Proprietà Java/TypeScript | Vincoli/default |
| --- | --- | --- | --- |
| `id` | `SERIAL` | `id` | PK, generato dal DB |
| `house_id` | `INT` | `houseId` | NOT NULL, FK → `house(id)` ON DELETE CASCADE |
| `user_id` | `INT` | `userId` | NOT NULL, FK → `app_user(id)` ON DELETE CASCADE |
| `role` | `VARCHAR(20)` | `role` | NOT NULL, `MEMBER`, CHECK `OWNER`/`MEMBER` |
| `active` | `BOOLEAN` | `active` | NOT NULL, `true` |

- `membership_user_house`: UNIQUE (`user_id`, `house_id`), una sola riga per
  coppia utente/casa, anche se inattiva. Un futuro rientro dovrà riattivare la
  riga esistente, senza inserirne un duplicato.
- `membership_one_active_per_user`: indice UNIQUE parziale su `user_id` con
  `WHERE active = TRUE`. Impedisce due appartenenze attive anche con richieste
  concorrenti; permette più appartenenze inattive verso case diverse.
- `membership_house_id`: indice per elenco membri e join per casa.

L'archiviazione della casa e la disattivazione dell'account non modificano
automaticamente `membership.active`. Il vincolo vale anche per un'appartenenza
attiva a una casa archiviata: per cambiarla occorrerà disattivare esplicitamente
la vecchia appartenenza. I controlli di autorizzazione saranno compito dei
servizi/controller futuri, inclusi creazione dell'OWNER, ingressi in case non
archiviate, trasferimento del responsabile e uscita con saldo zero.

### Vista `Member`

Il record Java `Member` corrisponde alla struttura piatta `Member` di
`frontend/src/app/core/models.ts`, senza ereditare `User` e senza password:

| Campi della vista | Origine |
| --- | --- |
| `id`, `name`, `email`, `role`, `active`, `color`, `photo` | `app_user` |
| `membershipId` | `membership.id` |
| `membershipRole` | `membership.role` |
| `memberActive` | `membership.active` |

`role` e `active` descrivono l'account; `membershipRole` e `memberActive`
descrivono l'appartenenza. Gli identificativi Java sono `Long`, coerenti con
il modello `User` esistente; in TypeScript restano `number`.

## Metodi DAO

Tutte le implementazioni sono bean `@Repository`, costruiti con lo stesso
`DataSource` di Spring. Usano SQL parametrizzato tramite `JdbcTemplate` e
`PreparedStatement`, senza ORM. Ogni metodo `save` esegue un INSERT: non è
un aggiornamento né un upsert. L'ID generato viene assegnato al modello passato.

| Interfaccia | Metodo | Contratto |
| --- | --- | --- |
| `HouseDAO` | `void save(House house)` | Inserisce la casa e assegna `id` e codice normalizzato |
| `HouseDAO` | `House findById(Long id)` | Restituisce la casa oppure `null` |
| `HouseDAO` | `House findByInviteCode(String inviteCode)` | Ricerca normalizzata, casa oppure `null`; può restituire una casa archiviata |
| `HouseDAO` | `House findActiveByUserId(Long userId)` | Casa collegata alla membership attiva, oppure `null`; non filtra `house.archived` o `app_user.active` |
| `MembershipDAO` | `void save(Membership membership)` | Inserisce l'appartenenza e assegna `id`; i vincoli DB tutelano FK, ruolo e unicità |
| `MembershipDAO` | `Membership findActiveByUserId(Long userId)` | Appartenenza con `active = true`, oppure `null` |
| `MembershipDAO` | `List<Member> findMembersByHouseId(Long houseId)` | Membri attuali ed ex, compresi account disattivati, ordinati per ID utente crescente; lista vuota se nessuno |

Per i soli membri attuali il chiamante filtra `memberActive`; non deve
confonderlo con lo stato di attivazione dell'account.

Le firme di `UserDAO` restano invariate (`findById`, `findByEmail`,
`findByEmailAndPassword`, `save`, `update`, `findAll`). `UserDAOJDBC` usa ora
`JdbcTemplate` per partecipare alle stesse transazioni degli altri DAO.
Gli errori SQL non vengono più solo stampati e ignorati: tutti i DAO
propagano `DataAccessException` runtime di Spring, ad esempio
`DuplicateKeyException` o `DataIntegrityViolationException`. Una lettura senza
risultati restituisce `null`/lista vuota; un errore DB non simula dati assenti.

## Transazioni tra più DAO

`JdbcTransactions` è un bean `@Component` con due metodi:

- `<T> T execute(Supplier<T> action)` per un'operazione con risultato;
- `void run(Runnable action)` per un'operazione senza risultato.

Usa `TransactionTemplate` con il `PlatformTransactionManager` JDBC
autoconfigurato da Spring Boot grazie allo starter JDBC già nel progetto.
Non servono nuove dipendenze, configurazioni manuali di connessione o ORM.

Esempio da un futuro servizio con `JdbcTransactions`, `HouseDAO` e
`MembershipDAO` ricevuti tramite constructor injection:

```java
House createWithOwner(House house, Long userId) {
    return transactions.execute(() -> {
        houseDAO.save(house);

        Membership owner = new Membership();
        owner.setHouseId(house.getId());
        owner.setUserId(userId);
        owner.setRole("OWNER");
        membershipDAO.save(owner);
        return house;
    });
}
```

I DAO riutilizzano la connessione associata alla transazione sul thread
corrente. Alla conclusione normale si esegue il commit; una runtime exception
o un `Error` che esce dal callback causa il rollback di tutte le scritture,
incluse eventuali operazioni `UserDAO`. Non intercettare e ignorare gli errori
nel callback: convertirli in risposte applicative solo fuori dal confine
transazionale. I callback non dichiarano checked exception; se necessario
propagarle come runtime exception.

La propagazione predefinita è `REQUIRED`: una chiamata annidata partecipa alla
transazione esterna, senza un commit indipendente. Anche un futuro servizio
Spring `@Transactional` può usare gli stessi DAO senza il wrapper. Tenere
le operazioni sullo stesso thread e usare sempre i bean con lo stesso
`DataSource`; niente connessioni JDBC aperte manualmente o commit nei DAO.
Fuori da una transazione esplicita, le singole operazioni restano utilizzabili
come nel codice esistente, con autocommit del datasource predefinito.

Il rollback riguarda il DB: gli ID già assegnati agli oggetti Java non vengono
annullati e le sequenze PostgreSQL possono presentare salti. Dopo un errore
scartare gli oggetti preparati per l'operazione fallita.

## Verifiche previste

La suite automatizzata e le istruzioni riproducibili sono descritte in
[`backend/TESTING.md`](../backend/TESTING.md). Il 14 novembre 2026 tutti i
28 test sono passati su Java 21 e PostgreSQL 18, in uno schema isolato.
Il database dell'applicazione non è stato modificato. Il seguente elenco
resta il riferimento per il collaudo delle integrazioni successive.

1. Su un DB di sviluppo già popolato, applicare lo schema e verificare che gli
   utenti esistenti rimangano invariati; riapplicarlo per controllare l'idempotenza.
2. Creare una casa e l'OWNER nella stessa transazione; rileggere per ID, codice
   con spazi/minuscole e utente; verificare tutti i campi e gli ID generati.
3. Verificare rifiuto di codici duplicati/non validi, ruoli diversi da
   OWNER/MEMBER, FK inesistenti, duplicazione utente/casa e seconda membership
   attiva, anche con operazioni concorrenti.
4. Verificare che membri attuali, ex e account disattivati siano distinguibili,
   ordinati per ID utente e privi di password nella vista `Member`.
5. Provocare un errore nella membership dopo l'inserimento della casa: nessuna
   casa deve restare salvata. Includere una scrittura `UserDAO` nel callback
   per controllarne il rollback insieme agli altri DAO; controllare anche il
   normale commit e l'utilizzo dei DAO fuori dal wrapper.
6. Riprovare registrazione, login, lettura sessione e salvataggio profilo con
   i controller esistenti dopo il passaggio di `UserDAOJDBC` a `JdbcTemplate`.
