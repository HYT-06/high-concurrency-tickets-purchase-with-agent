package com.example.hyt.agent.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.hyt.agent.dto.OrderInfo;
import com.example.hyt.agent.dto.TrainInfo;

/**
 * 只读查询 demo 库（ticket）。
 * <p>
 * 使用 JdbcTemplate 而非引入 MyBatis-Plus：agent 侧只有少量只读查询，
 * 保持依赖最轻；连接账号 agent_ro 仅有 SELECT 权限，从库另有 read_only=1 兜底。
 */
@Repository
public class TicketRepository {

    private final JdbcTemplate jdbc;

    public TicketRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 车次 ====================

    /**
     * 按出发站/到达站模糊查询车次。站名用 LIKE 匹配，因此"北京"能命中"北京南"。
     * date 为空时不限制日期。
     */
    public List<TrainInfo> findTrains(String fromStation, String toStation, String date) {
        StringBuilder sql = new StringBuilder("""
                SELECT number, name, type, departure, arrival, duration, price, date, status
                FROM train
                WHERE departure LIKE ? AND arrival LIKE ?
                """);
        List<Object> args = new java.util.ArrayList<>();
        args.add("%" + fromStation + "%");
        args.add("%" + toStation + "%");
        if (date != null && !date.isBlank()) {
            sql.append(" AND date = ?");
            args.add(date);
        }
        sql.append(" ORDER BY price, number");
        return jdbc.query(sql.toString(), TRAIN_MAPPER, args.toArray());
    }

    /** 按车次号查详情，返回 0 或 1 条，避免返回 null。 */
    public List<TrainInfo> findTrainByNumber(String trainNo) {
        return jdbc.query("""
                SELECT number, name, type, departure, arrival, duration, price, date, status
                FROM train
                WHERE number = ?
                """, TRAIN_MAPPER, trainNo);
    }

    /** 两站之间的票价，按车次号 -> 票价返回，用于比价。 */
    public Map<String, Double> findPrices(String fromStation, String toStation) {
        Map<String, Double> prices = new LinkedHashMap<>();
        jdbc.query("""
                SELECT number, price FROM train
                WHERE departure LIKE ? AND arrival LIKE ?
                ORDER BY price
                """, rs -> {
            prices.put(rs.getString("number"), rs.getDouble("price"));
        }, "%" + fromStation + "%", "%" + toStation + "%");
        return prices;
    }

    /** 系统内出现过的所有车站名（从车次表的出发站/到达站去重得到）。 */
    public List<String> findStationNames() {
        return jdbc.queryForList("""
                SELECT name FROM (
                    SELECT departure AS name FROM train
                    UNION
                    SELECT arrival AS name FROM train
                ) t ORDER BY name
                """, String.class);
    }

    // ==================== 订单 ====================

    /**
     * 按用户查订单。userId 必须能解析为数字，否则返回空列表
     * （user_id 是 BIGINT，直接拼字符串会导致 SQL 报错）。
     */
    public List<OrderInfo> findOrders(String userId, String status) {
        Long uid = parseUserId(userId);
        if (uid == null) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(ORDER_SELECT + " WHERE o.user_id = ?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(uid);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND o.status = ?");
            args.add(status.toLowerCase());
        }
        sql.append(" ORDER BY o.created_at DESC");
        return jdbc.query(sql.toString(), ORDER_MAPPER, args.toArray());
    }

    /** 按订单号查，返回 0 或 1 条，避免返回 null。 */
    public List<OrderInfo> findOrderById(String orderId) {
        Long oid = parseUserId(orderId);
        if (oid == null) {
            return List.of();
        }
        return jdbc.query(ORDER_SELECT + " WHERE o.id = ?", ORDER_MAPPER, oid);
    }

    private static Long parseUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 映射 ====================

    private static final String ORDER_SELECT = """
            SELECT o.id, o.price, o.date, o.status, o.created_at,
                   t.number AS train_no, t.departure, t.arrival,
                   s.seat_number
            FROM `order` o
            LEFT JOIN train t ON t.id = o.train_id
            LEFT JOIN seat s ON s.id = o.seat_id
            """;

    private static final org.springframework.jdbc.core.RowMapper<TrainInfo> TRAIN_MAPPER = (rs, i) -> new TrainInfo(
            rs.getString("number"),
            rs.getString("name"),
            rs.getString("type"),
            rs.getString("departure"),
            rs.getString("arrival"),
            rs.getString("duration"),
            rs.getDouble("price"),
            String.valueOf(rs.getDate("date")),
            rs.getString("status"));

    private static final org.springframework.jdbc.core.RowMapper<OrderInfo> ORDER_MAPPER = (rs, i) -> new OrderInfo(
            String.valueOf(rs.getLong("id")),
            rs.getString("train_no"),
            rs.getString("departure"),
            rs.getString("arrival"),
            String.valueOf(rs.getDate("date")),
            rs.getString("seat_number"),
            rs.getDouble("price"),
            rs.getString("status"),
            String.valueOf(rs.getTimestamp("created_at")));
}
