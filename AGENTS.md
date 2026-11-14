# Coordinamento del gruppo

Prima di iniziare un task o preparare un commit, leggere
`docs/00-STATO-GRUPPO.md`, il registro operativo condiviso del progetto.

Per ogni commit, incluso un commit di documentazione, aggiornare quel file
nello stesso commit con il risultato effettivo, le verifiche eseguite e il
prossimo passo di Antonio, David, Elisa e Giovanna. Se un prossimo passo non
cambia, mantenerlo esplicito. Indicare dipendenze, blocchi e stato di
integrazione; non dichiarare superati test o collaudi non eseguiti.

Per il commit corrente usare il titolo o «questo commit» nel registro;
al successivo aggiornamento completare il riferimento precedente con il suo
hash reale. Non creare un ciclo di commit per inserire l'hash del documento
nel documento stesso.

Seguire lo stack e i vincoli autorizzati nel task: Java 21, Spring Boot,
PostgreSQL e DAO JDBC. Preservare i dati e il lavoro locale degli altri membri.
Non eseguire commit, push o merge senza l'autorizzazione del relativo task.
