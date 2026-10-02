package com.hyt.test.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 读写分离路由验证。
 * <p>
 * 用 {@code SELECT @@port} 直接观察当前连接落在哪个实例上：
 * 主库 3306、从库 3307。这是唯一能确定"请求到底走了哪个库"的可靠方式。
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class ReadWriteSplitTests {

    private static final int MASTER_PORT = 3306;
    private static final int SLAVE_PORT = 3307;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private Integer currentPort() {
        return jdbc().queryForObject("SELECT @@port", Integer.class);
    }

    /** 无事务的查询必须留在主库，保证座位/用户等强一致读不回退到从库 */
    @Test
    void queryWithoutTransactionRoutesToMaster() {
        assertEquals(MASTER_PORT, currentPort(), "无事务时应走主库");
    }

    /** 写事务走主库 */
    @Test
    void writeTransactionRoutesToMaster() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Integer port = tx.execute(status -> currentPort());
        assertEquals(MASTER_PORT, port, "写事务应走主库");
    }

    /** @Transactional(readOnly = true) 走从库 */
    @Test
    void readOnlyTransactionRoutesToSlave() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        Integer port = tx.execute(status -> currentPort());
        assertEquals(SLAVE_PORT, port, "readOnly 事务应走从库");
    }

    /** 从库必须拒绝写入：服务端 read_only=1 是防误写的最后一道防线 */
    @Test
    void slaveRejectsWrites() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        assertThrows(Exception.class, () -> tx.execute(status -> {
            jdbc().update("INSERT INTO train(name, number, type, status, departure, arrival, duration, price, date) "
                    + "VALUES ('只读校验', 'ZZREADONLY', '高铁', '正常', 'A', 'B', '1h', 1.0, '2026-01-01')");
            return null;
        }), "从库不应允许写入");
    }
}
