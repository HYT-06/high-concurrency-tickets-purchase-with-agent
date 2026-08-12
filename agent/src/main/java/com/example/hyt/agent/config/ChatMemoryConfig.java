package com.example.hyt.agent.config;

import com.example.hyt.agent.service.UserChatService;
import com.example.hyt.agent.tools.*;
import com.example.hyt.agent.tracing.LoggingChatModelListener;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

@Configuration
public class ChatMemoryConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryConfig.class);

    @Autowired
    private RagInitService ragInitService;

    @Autowired
    private LoggingChatModelListener loggingChatModelListener;

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

    // 手动构建带 Listener 的 OpenAiChatModel，覆盖 starter 的自动配置
    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;
    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;
    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;
    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private Double temperature;
    @Value("${langchain4j.open-ai.chat-model.max-tokens:1024}")
    private Integer maxTokens;

    @Primary
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .listeners(List.of(loggingChatModelListener))
                .build();
    }

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
        EmbeddingStore<TextSegment> embeddingStore = ragInitService.getEmbeddingStore();
        EmbeddingModel embeddingModel = ragInitService.getEmbeddingModel();

        // RAG 检索器 + 日志包装
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();

        return AiServices.builder(UserChatService.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(memoryId -> chatMemoryProvider.apply(memoryId))
                .contentRetriever((query) -> {
                    long start = System.currentTimeMillis();
                    List<Content> hits = contentRetriever.retrieve(query);
                    long cost = System.currentTimeMillis() - start;
                    log.info("[RAG] 🔍 query=\"{}\" hits={} cost={}ms",
                            truncate(query.text(), 80), hits.size(), cost);
                    for (int i = 0; i < hits.size(); i++) {
                        TextSegment seg = hits.get(i).textSegment();
                        log.info("      [{}] score={} -> {}",
                                i + 1, String.format("%.3f", hits.get(i).score()), truncate(seg.text(), 120));
                    }
                    return hits;
                })
                .tools(trainQueryTool)
                .tools(orderQueryTool)
                .tools(refundPolicyTool)
                .tools(stationInfoTool)
                .tools(priceQueryTool)
                .build();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() > maxLen ? oneLine.substring(0, maxLen) + "..." : oneLine;
    }
}