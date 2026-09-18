package com.example.hyt.monitor.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 安全检测子 Agent
 * 专责：识别刷票行为、暴力登录、限流命中分析
 */
public interface SecurityAgent {

    @SystemMessage("""
            你是安全分析专家。你的任务是：
            1. 调用 queryAbnormalUsers 工具查询触发限流的用户列表
            2. 对可疑用户调用 queryUserDetail 工具查看其行为详情
            3. 分析行为模式：请求是否集中在单一接口、频率是否异常高
            4. 判断是否疑似脚本刷票
            
            回答格式：
            - 如果无异常：简洁说明"未发现异常用户"
            - 如果有异常：⚠️ 标出用户 ID、限流次数、请求特征，给出处置建议
            回答要简洁，不超过 150 字。
            """)
    String analyze(@UserMessage String question);
}
