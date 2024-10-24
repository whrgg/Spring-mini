package springJdbc.jdbc.transactional;

import jakarta.annotation.Nullable;

import java.sql.Connection;

public class TransactionalUtils {

    @Nullable
    public static Connection getCurrentConnection() {
        TransactionStatus ts = DataSourceTransactionManager.th.get();
        return ts == null ? null : ts.connection;
    }
}
