package it.coinquilini.dao;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Esegue più chiamate DAO sulla stessa transazione Spring/JDBC. */
@Component
public class JdbcTransactions {
    private final TransactionTemplate transactions;

    public JdbcTransactions(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    public void run(Runnable action) {
        transactions.executeWithoutResult(status -> action.run());
    }
}
