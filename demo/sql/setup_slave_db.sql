-- ============================================
-- MySQL 主从复制 - 从库搭建脚本
-- ============================================

-- 注意：以下操作需要在【主库】上执行

-- 1. 创建用于复制的用户（主库执行）
CREATE USER IF NOT EXISTS 'repl_user'@'%' IDENTIFIED BY 'repl_password';
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';
FLUSH PRIVILEGES;

-- 2. 查看主库状态（主库执行，记录 File 和 Position）
SHOW MASTER STATUS;

-- ============================================
-- 注意：以下操作需要在【从库】上执行
-- ============================================

-- 3. 从库需要先启动一个新的 MySQL 实例（端口 3307）
--    Docker 方式（推荐）：
--    docker run -d --name mysql-slave \
--      -p 3307:3306 \
--      -e MYSQL_ROOT_PASSWORD=123456 \
--      mysql:8.0

-- 4. 创建从库数据库（从库执行）
CREATE DATABASE IF NOT EXISTS ticket_slave CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 5. 配置从库连接主库（从库执行）
--    把下面的 MASTER_LOG_FILE 和 MASTER_LOG_POS 替换成步骤 2 查到的值
CHANGE MASTER TO
  MASTER_HOST='localhost',
  MASTER_PORT=3306,
  MASTER_USER='repl_user',
  MASTER_PASSWORD='repl_password',
  MASTER_LOG_FILE='mysql-bin.000001',  -- 替换成 SHOW MASTER STATUS 的 File
  MASTER_LOG_POS=156;                   -- 替换成 SHOW MASTER STATUS 的 Position

-- 6. 启动复制（从库执行）
START SLAVE;

-- 7. 查看从库复制状态（从库执行）
SHOW SLAVE STATUS;
-- 确认 Slave_IO_Running 和 Slave_SQL_Running 都是 Yes

-- ============================================
-- 从主库导出数据并导入到从库（首次同步）
-- ============================================

-- 在主库导出数据：
-- mysqldump -h localhost -P 3306 -u root -p123456 --single-transaction --master-data=2 ticket > ticket_backup.sql

-- 在从库导入数据：
-- mysql -h localhost -P 3307 -u root -p123456 ticket_slave < ticket_backup.sql

-- ============================================
-- 验证读写分离效果
-- ============================================

-- 写操作（INSERT/UPDATE/DELETE）会走主库
-- 读操作（SELECT）会走从库

-- 在主库插入数据后，从库应该自动同步