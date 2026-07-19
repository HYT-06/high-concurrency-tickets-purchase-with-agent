package com.example.hyt.agent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface UserChatService {

    @SystemMessage("""
            你是票务预订系统的智能客服助手。你可以帮助用户解决以下问题：
            - 车次查询：帮助用户了解有哪些车次、出发站、到达站、票价等信息
            - 购票流程：指导用户如何选择车次、选择座位、完成购票
            - 退改签规则：解答退票、改签的相关政策和操作流程
            - 座位类型：解释靠窗、过道、中间座位的区别
            - 订单问题：帮助用户查看订单状态、取消订单等

            请用中文回复，保持简洁、友好、专业。回复内容控制在200字以内。""")
    String chat(@MemoryId String userId, @UserMessage String message);
}