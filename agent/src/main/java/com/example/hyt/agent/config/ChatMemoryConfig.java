package com.example.hyt.agent.config;

import com.example.hyt.agent.service.UserChatService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

@Configuration
public class ChatMemoryConfig {

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
        return AiServices.builder(UserChatService.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(memoryId -> chatMemoryProvider.apply(memoryId))
                .build();
    }
}