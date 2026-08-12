package com.example.hyt.agent.dto;

/**
 * 退票计算结果
 */
public record RefundResult(
        double originalPrice,    // 原票价
        double refundFee,        // 退票手续费
        double actualRefund,     // 实际退款金额
        String feeReason         // 收费原因说明
) {
}