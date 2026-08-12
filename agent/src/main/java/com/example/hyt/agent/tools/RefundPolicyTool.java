package com.example.hyt.agent.tools;

import com.example.hyt.agent.dto.RefundResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 退票政策计算工具
 */
@Component
public class RefundPolicyTool {

    @Tool("根据开车时间和票价计算退票手续费")
    public RefundResult checkTicketRefundPolicy(
            @P("开车时间，格式 yyyy-MM-dd HH:mm") String departureTime,
            @P("票价") double price
    ) {
        LocalDateTime departure;
        try {
            departure = LocalDateTime.parse(departureTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return new RefundResult(price, 0, price, "时间格式错误");
        }

        LocalDateTime now = LocalDateTime.now();
        long hoursBeforeDeparture = Duration.between(now, departure).toHours();

        double feeRate;
        String reason;

        if (hoursBeforeDeparture >= 192) { // 8天及以上
            feeRate = 0;
            reason = "开车前8天以上，免费退票";
        } else if (hoursBeforeDeparture >= 48) { // 48小时~8天
            feeRate = 0.05;
            reason = "开车前48小时至8天内，收取5%手续费";
        } else if (hoursBeforeDeparture >= 24) { // 24~48小时
            feeRate = 0.10;
            reason = "开车前24至48小时内，收取10%手续费";
        } else if (hoursBeforeDeparture > 0) { // 24小时内
            feeRate = 0.20;
            reason = "开车前24小时内，收取20%手续费";
        } else {
            feeRate = 0.20;
            reason = "开车后需到车站窗口办理，收取20%手续费";
        }

        double fee = Math.round(price * feeRate * 100) / 100.0;
        double actualRefund = price - fee;

        return new RefundResult(price, fee, actualRefund, reason);
    }

    @Tool("检查是否可以改签到指定车次")
    public String checkChangePolicy(
            @P("原车次开车时间，格式 yyyy-MM-dd HH:mm") String originalDepartureTime,
            @P("是否已开车") boolean hasDeparted
    ) {
        LocalDateTime departure;
        try {
            departure = LocalDateTime.parse(originalDepartureTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return "时间格式错误";
        }

        LocalDateTime now = LocalDateTime.now();
        long hoursBeforeDeparture = Duration.between(now, departure).toHours();

        if (hasDeparted) {
            if (LocalDateTime.now().toLocalDate().equals(departure.toLocalDate())) {
                return "开车后当日24:00前可改签当日后续有余票车次，但改签后不可退票";
            }
            return "已过当日24:00，无法改签";
        }

        if (hoursBeforeDeparture >= 48) {
            return "开车前48小时以上，可改签预售期内任意车次";
        }
        return "开车前48小时以内，仅可改签当日及此前车次";
    }
}