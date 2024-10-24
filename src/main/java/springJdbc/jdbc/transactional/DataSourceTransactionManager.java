package springJdbc.jdbc.transactional;

import springJdbc.exception.TransactionException;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

public class DataSourceTransactionManager implements PlatformTransactionManager, InvocationHandler {

    static final  ThreadLocal<TransactionStatus> th =new ThreadLocal<>();
    final DataSource dataSource;

    public DataSourceTransactionManager(DataSource source){
        this.dataSource = source;
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        TransactionStatus ts = th.get();
        if(ts==null){
            try(Connection connection = dataSource.getConnection()){
                boolean autoCommit = connection.getAutoCommit();
                if(autoCommit){
                    connection.setAutoCommit(false);
                }
                try {
                    // 设置ThreadLocal状态:
                    th.set(new TransactionStatus(connection));
                    // 调用业务方法:
                    Object r = method.invoke(proxy, args);
                    // 提交事务:
                    connection.commit();
                    // 方法返回:
                    return r;
                }catch (InvocationTargetException e){
                    TransactionException te = new TransactionException(e.getCause());
                    try {
                        connection.rollback();
                    } catch (SQLException sqle) {
                        te.addSuppressed(sqle);
                    }
                    throw te;
                }finally {
                    th.remove();
                    connection.setAutoCommit(true);
                }
            }
        }else {
            // 当前已有事务,加入当前事务执行:
            return method.invoke(proxy, args);
        }
    }




}
