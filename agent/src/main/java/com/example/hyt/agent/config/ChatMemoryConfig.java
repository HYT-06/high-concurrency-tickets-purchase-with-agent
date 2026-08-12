package com.example.hyt.agent.config;

import com.example.hyt.agent.service.UserChatService;
import com.example.hyt.agent.tools.*;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

@Configuration
public class ChatMemoryConfig {

    @Autowired
    private RagInitService ragInitService;

    @Autowired
    private TrainQueryTool trainQueryTool;

    @Autowired
    private OrderQueryTool orderQueryTool;

    @Autowired
    private RefundPolicyTool refundPolicyTool;

    @Autowired
    private StationInfoTool stationInfoTool;

    @Autowired
    private PriceQueryTool priceQueryTool;

    @Bean
    public InMemoryChatMemoryStore chatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }

    @Bean
    public Function<Object, ChatMemory> chatMemoryProvider(InMemoryChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(chatMemoryStore)
                .maxMessages(20)
                .build();
    }

    @Bean
    public UserChatService userChatService(ChatLanguageModel chatLanguageModel,
                                           Function<Object, ChatMemory> chatMemoryProvider) {
        // 获取向量库和向量模型
        EmbeddingStore<TextSegment> embeddingStore = ragInitService.getEmbeddingStore();
        EmbeddingModel embeddingModel = ragInitService.getEmbeddingModel();

        // 构建 RAG 内容检索器
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();

        return AiServices.builder(UserChatService.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(memoryId -> chatMemoryProvider.apply(memoryId))
                .contentRetriever(contentRetriever)
                // 注册工具
                .tools(trainQueryTool)
                .tools(orderQueryTool)
                .tools(refundPolicyTool)
                .tools(stationInfoTool)
                .tools(priceQueryTool)
                .build();
    }
}