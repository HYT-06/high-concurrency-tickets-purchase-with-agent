-- ============================================================
-- MySQL 主从复制搭建脚本
-- 环境：MySQL 8.0.43（Windows）
-- 主库：3306（server-id=1，datadir 由 MySQL 服务自带配置决定）
-- 从库：3307（server-id=2，datadir=G:/JAVA/mysql-slave-data，配置见 sql/slave/my.ini）
--
-- 【重要】本文件原版用的是 CHANGE MASTER TO / START SLAVE / SHOW SLAVE STATUS，
-- 这些语法在 MySQL 8.0.23+ 已废弃、8.4 起被移除，务必改用下面的新语法：
--   CHANGE MASTER TO          -> CHANGE REPLICATION SOURCE TO
--   START SLAVE / STOP SLAVE  -> START REPLICA / STOP REPLICA
--   SHOW SLAVE STATUS         -> SHOW REPLICA STATUS
--
-- 【环境坑】从库配置文件的路径不要放在含非 ASCII 字符的目录下
-- （本项目路径含中文破折号，实测 mysqld --defaults-file 会把路径截断，
--   报 "Failed to open required defaults file"）。
-- ============================================================


-- ============================================
-- 步骤 1：主库 —— 确认复制前置条件
-- ============================================
-- 必须 log_bin=ON；server_id 不能与从库相同；binlog_format 建议 ROW
SHOW VARIABLES WHERE Variable_name IN ('log_bin', 'server_id', 'binlog_format', 'binlog_row_image');
SHOW MASTER STATUS;   -- 记录 File 与 Position


-- ============================================
-- 步骤 2：主库 —— 创建复制账号
-- ============================================
CREATE USER IF NOT EXISTS 'repl_user'@'%' IDENTIFIED BY 'repl_password';
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';
FLUSH PRIVILEGES;


-- ============================================
-- 步骤 3：从库 —— 初始化实例（命令行执行，非 SQL）
-- ============================================
-- 3.1 初始化 datadir（root 初始无密码）
--   mysqld --defaults-file="G:/JAVA/mysql-slave/my.ini" --initialize-insecure --console
-- 3.2 启动实例
--   mysqld --defaults-file="G:/JAVA/mysql-slave/my.ini"
-- 3.3 设置 root 密码（与主库一致）
--   ALTER USER 'root'@'localhost' IDENTIFIED BY '123456';


-- ============================================
-- 步骤 4：主库 —— 导出全量快照（命令行执行）
-- ============================================
-- --source-data=2 会把 binlog 位点以注释形式写进 dump，
-- 便于对齐复制起点；--single-transaction 保证 InnoDB 一致性快照且不锁表。
--   mysqldump -h localhost -P 3306 -u root -p123456 \
--     --single-transaction --source-data=2 \
--     --databases ticket media ticketing \
--     --result-file=G:/JAVA/mysql-slave/ticket_dump.sql
--
-- 从 dump 文件头部可以看到类似：
--   -- CHANGE MASTER TO MASTER_LOG_FILE='binlog.000939', MASTER_LOG_POS=868;
-- 记下这两个值。


-- ============================================
-- 步骤 5：从库 —— 导入快照
-- ============================================
-- 用 mysql 客户端的 source 命令（PowerShell 下不支持 < 重定向）：
--   mysql -h localhost -P 3307 -u root -p123456 \
--     --default-character-set=utf8mb4 \
--     -e "source G:/JAVA/mysql-slave/ticket_dump.sql"


-- ============================================
-- 步骤 6：从库 —— 建立并启动复制
-- ============================================
-- 把 SOURCE_LOG_FILE / SOURCE_LOG_POS 换成步骤 4 记录的值
STOP REPLICA;
RESET REPLICA ALL;

CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='localhost',
  SOURCE_PORT=3306,
  SOURCE_USER='repl_user',
  SOURCE_PASSWORD='repl_password',
  SOURCE_LOG_FILE='binlog.000939',
  SOURCE_LOG_POS=868;

START REPLICA;


-- ============================================
-- 步骤 7：验证复制状态
-- ============================================
SHOW REPLICA STATUS;
-- 关键字段必须为：
--   Replica_IO_Running: Yes
--   Replica_SQL_Running: Yes
--   Seconds_Behind_Source: 0
--
-- 常见报错：
--   MY-013117 "source and replica have equal MySQL server ids"
--     -> 主从 server_id 相同，检查 sql/slave/my.ini 里的 server-id=2
--   MY-010257 "Do you already have another mysqld server running on port 3306"
--     -> 从库端口没改，两个实例在抢 3306，检查 my.ini 里的 port=3307


-- ============================================
-- 步骤 8：端到端验证同步（主库执行后到从库确认）
-- ============================================
-- 主库：
--   INSERT INTO ticket.seat (train_id, seat_number, status) VALUES (1,'ZZTEST','available');
-- 从库（应能查到 id=16 的 ZZTEST）：
--   SELECT * FROM ticket.seat WHERE seat_number='ZZTEST';
-- 主库清理：
--   DELETE FROM ticket.seat WHERE seat_number='ZZTEST';
-- 从库（应查不到）：
--   SELECT COUNT(*) FROM ticket.seat WHERE seat_number='ZZTEST';


-- ============================================
-- 【已知限制】MySQL 8.0.43 已移除 min_replicas_to_write / min_replicas_max_lag
-- ============================================
-- 这两个变量原本用于「写法定人数」保护：从库滞后时主库拒绝写入，
-- 从而避免故障转移丢数据。实测在 8.0.43 上配置会直接启动失败：
--   [ERROR] [MY-000067] unknown variable 'min-replicas-to-write=1'
-- 因此当前版本下无法用配置规避「主从异步复制丢写」的风险，只能靠应用层保证：
-- 涉及库存正确性的写操作必须走主库（见 DataSourceConfig 的路由规则）。
