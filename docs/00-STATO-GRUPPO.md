# Registro del gruppo — stato e prossimo passo

Aggiornato: **14 novembre 2026**. Leggere questo file prima di iniziare un task
e aggiornarlo **nello stesso commit di ogni modifica**, anche di documentazione.
È il punto di ripartenza condiviso del gruppo.

## Checkpoint corrente

- Base condivisa: main `301fde5`, con fondamenta JDBC e autenticazione BCrypt.
- Branch del traguardo: `codex/casa-inviti-snapshot`.
- Backend casa/inviti/snapshot: **implementato e testato**.
- Collegamento Angular: **pronto nella cartella di lavoro; da includere nel commit successivo**.
- Verifiche locali del traguardo completo: **87 test passati**, build frontend,
  packaging/avvio backend e collaudo UI con due utenti riusciti.
- **Prova sul PC di David: DA ESEGUIRE.** Il risultato su quel computer
  non è stato ancora verificato.
- **Merge del traguardo in main: DA ESEGUIRE dopo prova e review.**
  Fare pull di main non porta automaticamente il nuovo lavoro del branch.
- Raccogliere l'esito di David prima del prossimo traguardo; correggere qui
  eventuali problemi di questo blocco.

Lo schema attuale ha tre tabelle: app_user, house, membership. Gli altri otto
insiemi di dati di dominio sono ancora da implementare. Lo snapshot ha dati
reali per utente/casa/membri; gli altri array sono vuoti e il saldo zero è
provvisorio. Finanze e dashboard completa rimangono aperte.

## Prossimo passo di ciascun membro

| Membro | Prima azione | Prossimo task dopo verifica/integrazione | Criterio di completamento |
| --- | --- | --- | --- |
| **Antonio — snoxzy / 0xSnoxzy** | Coordinare il collaudo su PC David, correggere problemi e integrare dopo review. | **M2b**: modifica nome/emoji/foto casa, rigenerazione invito e trasferimento OWNER. | Operazioni reali riservate all'OWNER, vecchio invito invalidato, trasferimento atomico e test pertinenti passati. |
| **David — EsBru** | Scaricare questo branch sul proprio PC, eseguire la checklist e riportare l'esito. | **M3**, poi **M4**: schema/DAO spese e quote, CRUD e Proxy; quindi saldi, rimborsi e pareggi. | 1000 centesimi su tre quote senza perdita, transazioni e lazy loading/cache verificati; poi saldi coerenti e rimborsi verso zero. |
| **Elisa** | Dopo il merge aggiornare la base e concordare tabelle/contratti con il gruppo. | **M5**: schema/DAO shopping_item e purchase, CRUD lista, spunta, duplicati e frequenti. **M6 checkout** dopo M3/M5. | Quantità persistenti, duplicati sommati, frequenti dallo storico; checkout interamente atomico. |
| **Giovanna** | Dopo il merge aggiornare la base e riusare lo snapshot esistente. | **M7a/M7b**: schema/DAO task e turn, definizioni, primo turno, completamento/rotazione/storico; poi **M8** avvisi. | Un turno pendente, WEEKLY a +7 giorni, AUTO circolare per ID, storico preservato e isolamento per casa. |

Prima del lavoro parallelo concordare **chi modifica schema.sql e quali tabelle
aggiunge**. Sono possibili aggiunte per modulo oppure un commit condiviso:
non duplicare implementazioni divergenti. Mantenere Java 21, Spring Boot,
PostgreSQL e DAO JDBC.

## Ordine successivo e dipendenze

| Traguardo | Responsabile | Dipendenza |
| --- | --- | --- |
| M2b — impostazioni casa/inviti/OWNER | Antonio | Blocco casa attuale integrato. |
| M3 — spese/quote/Proxy | David | Casa/sessione disponibili e schema concordato. |
| M4 — saldi/rimborsi | David | M3. |
| M2c — uscita/rimozione con saldo zero e ultima casa | Antonio | M4: serve verificare realmente crediti e debiti. |
| M5 — lista/frequenti | Elisa | Casa/sessione e tabelle dedicate. |
| M6 — checkout atomico | Elisa con David | M3 + M5. |
| M7/M8 — mansioni, turni e avvisi | Giovanna | Casa/sessione e tabelle dedicate. |
| M9 finale — snapshot completo, dashboard, Per me e Chart.js | Giovanna con David | Dati reali dei moduli; estendere lo snapshot esistente. |
| M10 — amministrazione e revisione protezioni | Antonio | API di dominio disponibili; applicare i controlli già a ogni nuova API. |
| M11 — dump, avvio pulito, collaudo e quattro relazioni | Tutti, Elisa coordina | Moduli completati e integrati. |

M2b, M3, M5 e M7 possono procedere in parallelo dopo la base condivisa.
Il Modulo Casa completo termina con M2c; il blocco attuale è M2a con snapshot
iniziale. Non serve attendere tutta l'applicazione per integrare una milestone.

## Registro dei commit rilevanti

| Riferimento | Autore | Risultato | Prossimo passo |
| --- | --- | --- | --- |
| `f66a85c` | David | Fondamenta JDBC case/membri e 28 test, integrati in main. | Usare DAO e transazioni nei servizi. |
| `020ecbb` | Antonio | BCrypt, sessioni, profilo e 33 test, integrati in main. | Collegare casa e inviti reali. |
| `301fde5` | Antonio | Contratto fondamenta allineato a BCrypt; base main. | Implementare M2a e snapshot iniziale. |
| `questo commit` — feat(houses): creazione inviti e snapshot con test PostgreSQL | Antonio | API casa, DAO estesi, snapshot, vista pubblica e 26 test nuovi. | Collegare il frontend e verificare sul PC di David. |
| `previsto nel commit successivo` — feat(store): collega casa e membri alle API reali | Antonio | Store reale, ricaricamento, aggiornamento e demo separata. | Prova su PC David, poi review e merge. |
| `previsto nel commit documentazione` — docs: registro condiviso e collaudo sul PC di David | Antonio | Registro, regola di aggiornamento e guide riproducibili. | David esegue la checklist e il gruppo registra l'esito. |

«Questo commit» indica il commit che contiene quella versione del documento.
Al successivo aggiornamento completare il riferimento precedente con l'hash
letto da git log; evitare commit extra per inseguire l'hash del documento stesso.

```bash
git log -1 --oneline
git status --short
```

## Prova sul PC di David

### Scaricare il branch corretto

Dalla cartella del repository controllare prima il lavoro locale:

```bash
git status --short
git fetch origin
git switch --track origin/codex/casa-inviti-snapshot
```

Lo switch sopra è per il primo checkout. Se il branch esiste già, usare:

```bash
git switch codex/casa-inviti-snapshot
git pull --ff-only
```

Non usare reset o cancellazioni per aggirare modifiche locali. Le porte
3000/8080 devono essere disponibili per questa istanza dell'app.

### Preparare l'ambiente

Servono JDK 21, Maven, Node.js 24 e PostgreSQL. Con pgAdmin creare un database
di prova **nuovo e vuoto**, ad esempio coinquilini_prova. Non sostituire o
cancellare database esistenti. Non serve un dump per questo traguardo.

Esempio PowerShell, dalla cartella backend. Adeguare percorso JDK, porta e
database al PC; le credenziali sono quelle PostgreSQL locali di David:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
$dbCredential = Get-Credential -Message 'Credenziali PostgreSQL locali'
$env:DB_USERNAME = $dbCredential.UserName
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
$env:DB_URL = 'jdbc:postgresql://localhost:5432/coinquilini_prova'
$env:TEST_DATABASE_URL = $env:DB_URL
$env:TEST_DATABASE_USER = $env:DB_USERNAME
$env:TEST_DATABASE_PASSWORD = $env:DB_PASSWORD
mvn --batch-mode --no-transfer-progress test
```

Il ruolo di test deve poter creare schemi. Le suite ne generano uno ciascuna,
isolato, ed eliminano solo quello creato dalla suite. L'esito atteso sul codice
attuale è **87 test, 0 fallimenti/errori/saltati**; quello sul PC di David resta
da registrare. Le variabili valgono per quel terminale. Dopo i test, lì avviare:

```powershell
mvn spring-boot:run
```

In un secondo terminale, dalla cartella frontend:

```powershell
npm.cmd ci
npm.cmd run build
cd ..
npm.cmd run demo
```

La build richiede rete per il font Google già referenziato dal CSS.
Non committare node_modules, dist, target o credenziali.

### Percorso utente da verificare

Aprire http://localhost:3000:

- [ ] Registrare un utente dal modulo reale, senza scegliere un profilo demo.
- [ ] Creare la casa e vedere Responsabile nelle Impostazioni.
- [ ] Copiare il codice invito dalle Impostazioni.
- [ ] In un altro browser/finestra privata registrare un secondo utente.
- [ ] Entrare nella stessa casa con il codice, anche minuscolo/con spazi.
- [ ] Vedere gli stessi due membri e i ruoli OWNER/MEMBER corretti.
- [ ] Sul primo utente premere Aggiorna dati e ritrovare il secondo membro.
- [ ] Premere F5 in entrambi: identità e casa restano corrette.
- [ ] Salvare il profilo, ricaricare e controllare il dato aggiornato.
- [ ] Fare logout/login e verificare il rifiuto della password errata.
- [ ] Le funzioni non implementate segnalano il limite senza simulare salvataggi.

Spese/saldi/lista/mansioni/avvisi/admin non sono ancora completi. Modifica casa,
rigenerazione invito, cambio OWNER e uscita sono i prossimi task, non errori
di questo collaudo.

### Esito da compilare prima del merge

- Stato: **DA ESEGUIRE**
- Autore della prova: **da compilare**
- Data e commit provato: **da compilare**
- Sistema operativo / Java / Node / PostgreSQL: **da compilare**
- Test automatici: **da compilare**
- Build frontend e avvio backend: **da compilare**
- Percorso a due utenti: **da compilare**
- Problemi e passi per riprodurli: **da compilare**
- Decisione del gruppo su review/merge: **da compilare**

Il risultato sul PC di Antonio non sostituisce questa prova. Se emergono
problemi, correggerli sul branch e aggiornare qui il riferimento e le verifiche.

## Manutenzione a ogni commit

1. Aggiornare cosa è completato, cosa resta e le verifiche realmente eseguite.
2. Aggiungere una riga con autore, risultato e prossimo passo.
3. Aggiornare il prossimo task di **tutti e quattro** i membri; se invariato,
   mantenerlo esplicito senza inventare un nuovo task.
4. Indicare dipendenze e attese, distinguendo locale, sul branch e in main.
   Non dichiarare riuscito un collaudo non eseguito.
5. Includere questo file nello stesso commit, anche per modifiche di documentazione.

Il commit salva un risultato coerente; il push lo condivide sul branch;
review e merge lo portano in main. Dopo il merge il gruppo aggiorna la base
preservando modifiche e documenti locali.
