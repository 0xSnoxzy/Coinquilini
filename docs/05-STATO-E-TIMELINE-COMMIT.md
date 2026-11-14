# Stato reale del progetto e timeline dei commit

Aggiornamento: **14 novembre 2026**, dopo il traguardo casa/inviti.
Base condivisa: `main` a `301fde5`. Il traguardo verificato è stato diviso nei
commit backend `34617fb` e frontend `5e8e6c0`, sul branch
`codex/casa-inviti-snapshot`, pronto per il collaudo sul PC di David.
Il riferimento da aggiornare a ogni commit è ora
[Registro del gruppo](00-STATO-GRUPPO.md), con prossimo task di ogni membro e checklist.

## Situazione attuale

**Autenticazione, profilo, creazione casa e ingresso con invito funzionano con
dati reali. Il caricamento iniziale di casa e membri è collegato al database.
Il resto dei moduli di dominio è ancora da realizzare.**

| Area | Stato verificato |
| --- | --- |
| UI Angular | Grafica e CSS mantenuti; build production riuscita. |
| Stack | Java 21, Spring Boot 3.3.4, PostgreSQL e DAO JDBC, senza ORM. |
| Autenticazione | BCrypt, email normalizzate, login/logout/me, sessioni con solo userId e controllo utente attivo. Già integrata in main. |
| Profilo | Nome, colore e foto persistenti; dati aggiornati nello snapshot. Verificato anche il salvataggio dalla pagina Impostazioni. |
| Creazione casa | API reale, codice univoco e casa + OWNER nella stessa transazione. Implementata localmente in questo traguardo. |
| Ingresso con invito | API reale, normalizzazione codice, rifiuto seconda casa/casa archiviata e riattivazione dell'ex membro come MEMBER. Implementato localmente. |
| Casa e membri nello Store | Snapshot iniziale reale, ricaricamento e Aggiorna dati preservano identità e casa. Demo separata. |
| Altra gestione casa | Modifica casa, rigenerazione invito, trasferimento OWNER, uscita e rimozione ancora da implementare. |
| Spese/saldi/rimborsi/Proxy | UI presente; modelli backend, DAO, API e calcoli mancanti. |
| Lista/checkout/frequenti | UI presente; persistenza e operazioni reali mancanti. |
| Mansioni/turni/avvisi | UI presente; persistenza, rotazione e API mancanti. |
| Dashboard completa | Mostra casa e membri reali. Gli array dei moduli mancanti sono vuoti e il saldo è provvisoriamente zero; non sono calcoli economici. |
| Admin e consegna | API admin, controlli dei futuri moduli, dump completo, README finale e collaudo su secondo computer ancora da completare. |

Lo schema versionato definisce **3 tabelle su 11**: `app_user`, `house`,
`membership`. Le altre otto sono ancora da introdurre: `expense`,
`expense_share`, `repayment`, `shopping_item`, `purchase`, `task`,
`turn`, `notice`. Il requisito DAO su almeno cinque tabelle non è ancora
soddisfatto. Il traguardo casa non ha modificato schema.sql o models.ts.

## Timeline del lavoro effettuato

| Ordine | Commit | Autore | Risultato | Stato |
| --- | --- | --- | --- | --- |
| 1 | `e3d26a5` | snoxzy | Interfaccia Angular e scenari demo. | In main. |
| 2 | `a7a0519` | snoxzy | Prima autenticazione/profilo. | In main; versione iniziale superata da BCrypt. |
| 3 | `1a0075c` | snoxzy | Inizializzazione automatica schema. | In main. |
| 4 | `f66a85c` | David | Fondamenta JDBC case/membri, transazioni e 28 test. | Integrato in main con il suo autore. |
| 5 | `020ecbb` | snoxzy / Antonio | BCrypt, sessioni, profilo, configurazione DB esterna e 33 test. | Integrato in main. |
| 6 | `301fde5` | snoxzy | Allineamento del contratto fondamenta a BCrypt. | Base main usata per il nuovo traguardo. |
| 7 | `34617fb` | snoxzy / Antonio | Backend casa/inviti/snapshot e 26 test nuovi. | Branch `codex/casa-inviti-snapshot`, da integrare dopo collaudo/review. |
| 8 | `5e8e6c0` | snoxzy / Antonio | Store Angular reale e separazione demo. | Stesso branch; da verificare sul PC di David. |
| 9 | Questo commit di documentazione | snoxzy / Antonio | Registro condiviso, regola AGENTS e guide al collaudo. | Il riferimento corrente e i prossimi passi sono in `00-STATO-GRUPPO.md`. |

I primi tre commit sono del 26 settembre 2026; i successivi del 14 novembre.
I dati delle vecchie password in chiaro non sono stati convertiti automaticamente.

## Esito delle verifiche del traguardo casa

- **87 test passati**, nessun fallimento, errore o saltato: 33 autenticazione,
  28 fondamenta e 26 casa/snapshot su PostgreSQL 18 separato, con Java 21.0.3.
- Build Angular production riuscita; packaging e avvio reale del JAR riusciti.
- Browser: primo utente crea la casa, secondo utente entra con codice minuscolo;
  entrambi vedono gli stessi due membri e i ruoli OWNER/MEMBER corretti.
- Verificati ricaricamento, Aggiorna dati senza mock e profilo dalla UI.
- Testati anche rollback della creazione, collisioni del codice, concorrenza,
  isolamento tra case e rifiuto degli utenti disattivati.
- Nessuna cancellazione dei dati applicativi; ambiente personale separato sulla
  porta PostgreSQL 15432 e test automatici in schemi distinti.

Dettagli tecnici, limiti e comandi sono in [Casa/inviti/snapshot](07-CASA-INVITI-SNAPSHOT.md)
e [TESTING.md](../backend/TESTING.md). Per provare personalmente vedere
[la guida pratica](06-PROVA-PERSONALE.md).

## Prossimi commit e integrazione

Il lavoro casa attuale è pronto per il collaudo sul PC di David e la revisione.
La divisione effettiva è:

1. `34617fb` — `feat(houses): creazione inviti e snapshot con test PostgreSQL`
2. `5e8e6c0` — `feat(store): collega casa e membri alle API reali`
3. `docs: registro condiviso e collaudo sul PC di David`

Ogni commit deve restare compilabile: il backend e i test casa usano la vista
utente condivisa e lo snapshot iniziale. Il frontend dipende dalle nuove API.
Dopo la review e il collaudo del gruppo, il branch può essere integrato in
main senza aspettare spese o gli altri moduli.

## Timeline dei traguardi ancora da realizzare

Le sigle M0–M11 riprendono la roadmap semplificata. Sono risultati verificabili,
non date obbligatorie e non commit già esistenti.

| Ordine indicativo | Traguardo e responsabile | Commit suggerito | Criterio per chiuderlo |
| --- | --- | --- | --- |
| 1 | M0 restante — Antonio/David | `chore(db): tabelle dei moduli di dominio` | Otto tabelle concordate, inizializzazione ripetibile e dati preesistenti preservati. Coordinare lo schema condiviso. |
| 2 | M2b — Antonio | `feat(houses): impostazioni inviti e trasferimento owner` | Salvataggio casa, rigenerazione codice e trasferimento responsabile reali, consentiti solo all'OWNER. |
| 3 | M3 — David | `feat(finance): spese quote e proxy casa-spese` | CRUD autorizzato, quote atomiche e centesimi corretti; lazy loading e cache Proxy verificati. |
| 4 | M4 — David | `feat(balances): saldi rimborsi e pareggi` | Saldi con somma zero, rimborsi che riducono i debiti, suggerimenti e cancellazioni coerenti. Dipende da M3. |
| 5 | M2c — Antonio | `feat(houses): uscita e rimozione con saldo zero` | Uscita/rimozione vietata con credito o debito; gestione OWNER e archivio ultima casa. Dipende dai saldi. |
| 6 | M5 — Elisa | `feat(shopping): lista duplicati e frequenti` | CRUD/spunta persistenti, quantità e duplicati corretti, frequenti dallo storico. |
| 7 | M6 — Elisa con David | `feat(checkout): acquisto storico e spesa atomici` | Storico, rimozione prodotti e spesa/quote insieme; errore con rollback totale. Dipende da M3/M5. |
| 8 | M7a — Giovanna | `feat(tasks): definizioni e primo turno` | Creazione/modifica con un solo turno pendente, permessi e isolamento. |
| 9 | M7b — Giovanna | `feat(tasks): completamento rotazione e storico` | ONCE/WEEKLY, AUTO/MANUAL, avanzamento sette giorni e storico preservato. |
| 10 | M8 — Giovanna | `feat(notices): avvisi persistenti della casa` | Salvataggio/eliminazione e letture autorizzate solo per la casa corretta. |
| 11 | M9 finale — Giovanna/David | `feat(dashboard): snapshot completo e grafici reali` | Estendere lo snapshot esistente; spese, lista, turni, avvisi, saldi, Per me e Chart.js su dati reali. |
| 12 | M10 — Antonio | `feat(admin): utenti case e protezioni globali` | API ADMIN, blocchi, protezione ultimo admin e controllo autorizzazioni di tutti i flussi. |
| 13 | M11 — Tutti | `chore(release): dump demo readme e collaudo finale` | Import e avvio su ambiente pulito, prove multiutente/responsive, quattro relazioni e consegna. |

I lavori di David, Elisa e Giovanna possono procedere in parallelo con la
gestione casa di Antonio, una volta condivisi schema e contratti necessari.
Checkout attende spese/lista; uscita casa attende i saldi; dashboard completa
attende i moduli di dominio. Autenticazione e isolamento vanno applicati
subito a ogni nuovo endpoint, senza attendere il modulo admin.

## Attenzioni per i prossimi moduli

- `app_user.password` contiene l'hash BCrypt; non rinominare la colonna
  basandosi soltanto sulla proposta `password_hash` delle specifiche.
- Il rientro in casa riattiva già la coppia utente/casa esistente; non inserirne
  un duplicato e non ripristinare automaticamente un vecchio ruolo OWNER.
- `Expense.shared` è nei modelli TypeScript ma manca nello schema SQL proposto:
  concordarne persistenza o derivazione prima di M3.
- Con saldo positivo = credito, la formula coerente è
  `pagato - quote + rimborsi effettuati - ricevuti`; il testo delle specifiche
  inverte i segni dei rimborsi. Un debitore a -500 che rimborsa 500 deve arrivare a zero.
- Lo schema proposto usa `turn.task_id ON DELETE CASCADE`, mentre il requisito
  richiede conservare i turni completati. Concordare prima di M7 una soluzione
  che preservi lo storico.
- Lo snapshot attuale ha liste vuote per i moduli non implementati: sostituirle
  progressivamente con letture reali. Non considerare saldo e grafici già completati.

## Quando fare commit, push e merge

**Commit:** una modifica con obiettivo preciso, compilabile e con i test
pertinenti passati. Codice e test dello stesso risultato possono stare insieme.
Non occorre attendere un intero macro-modulo.

**Push:** pubblica i commit sul proprio branch; non aggiorna automaticamente main.

**Pull Request e merge:** presentano il risultato al gruppo e lo integrano in
main dopo verifiche e review. La roadmap propone la review di almeno un compagno.

**Dopo il merge:** aggiornare la base condivisa preservando modifiche locali e
documenti non tracciati. Evitare `git add .` o reset indiscriminati.
