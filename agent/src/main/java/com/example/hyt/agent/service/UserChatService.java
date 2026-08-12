package com.example.hyt.agent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface UserChatService {

    @SystemMessage("""
            你是12306票务预订系统的智能客服助手。

            【回答原则】
            1. 仅回答与铁路客票相关的问题，包括：购票、退票、改签、候补、预售期、座位类型、身份核验等
            2. 必须基于知识库中的规则回答，不要编造信息
            3. 如果问题与铁路客票无关，或知识库中没有相关信息，请回复："抱歉，我只能回答与12306铁路客票相关的问题，如购票、退改签、候补等，请问有这方面的问题吗？"

            【回答格式】
            - 简洁专业，控制在150字以内
            - 涉及规则时，准确引用知识库内容
            - 友好礼貌，使用中文回复""")
    String chat(@MemoryId String userId, @UserMessage String message);
}