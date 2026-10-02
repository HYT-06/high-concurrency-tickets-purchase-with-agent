package com.hyt.test.demo.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.zaxxer.hikari.HikariDataSource;

/**
 * MySQL 主从读写分离。
 *
 * <p>路由规则：处于 {@code @Transactional(readOnly = true)} 事务中的查询走从库，
 * 其余一律走主库。
 *
 * <p>判定放在 {@link AbstractRoutingDataSource#determineCurrentLookupKey()} 里而不是用 AOP，
 * 是因为该回调只在「获取连接」那一刻触发，此时 Spring 已把 readOnly 标记写入事务同步器，
 * 能读到准确值；用 AOP 反而要额外处理切面与事务切面的先后顺序。
 *
 * <p><b>哪些查询不能加 readOnly（必须走主库）：</b>
 * <ul>
 *   <li>座位查询（{@code seat}）——库存预热与下单校验依赖它，读从库的延迟数据会把
 *       已售座位当成可售，导致超卖</li>
 *   <li>用户查询（{@code user}）——登录/注册查重，刚写入的数据从库可能还没同步</li>
 * </ul>
 * 因此只有订单查询和车次列表这类「允许秒级延迟」的读才下沉到从库。
 */
@Configuration
public class DataSourceConfig {

    public static final String MASTER = "master";
    public static final String SLAVE = "slave";

    @Bean
    @ConfigurationProperties("app.datasource.master")
    public DataSource masterDataSource() {
        return new HikariDataSource();
    }

    @Bean
    @ConfigurationProperties("app.datasource.slave")
    public DataSource slaveDataSource() {
        return new HikariDataSource();
    }

    @Primary
    @Bean
    public DataSource dataSource(@Qualifier("masterDataSource") DataSource master,
                                 @Qualifier("slaveDataSource") DataSource slave) {
        Map<Object, Object> targets = new HashMap<>();
        targets.put(MASTER, master);
        targets.put(SLAVE, slave);

        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                if (TransactionSynchronizationManager.isActualTransactionActive()
                        && TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    return SLAVE;
                }
                return MASTER;
            }
        };
        routing.setDefaultTargetDataSource(master);
        routing.setTargetDataSources(targets);
        routing.afterPropertiesSet();

        // 【必须包一层懒连接代理】DataSourceTransactionManager 在 doBegin() 阶段、
        // 也就是事务刚开启时就立即取连接，而 readOnly 标记要等到 prepareSynchronization()
        // 才写入事务同步器——两者顺序决定了直接路由时永远读到 readOnly=false，
        // 结果所有查询都会落到主库（实测验证过）。
        // LazyConnectionDataSourceProxy 把真正的取连接推迟到第一条 SQL 执行时，
        // 那时事务已完全初始化，readOnly 能正确读到。
        return new LazyConnectionDataSourceProxy(routing);
    }
}
