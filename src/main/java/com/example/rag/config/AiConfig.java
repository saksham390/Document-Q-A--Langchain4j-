package com.example.rag.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeServerlessIndexConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    ChatModel chatModel(
            @Value("${rag.openai.chat-model}") String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(requiredEnvironment("OPENAI_API_KEY"))
                .modelName(modelName)
                .temperature(0.0)
                .build();
    }

    @Bean
    EmbeddingModel embeddingModel(
            @Value("${rag.openai.embedding-model}") String modelName) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(requiredEnvironment("OPENAI_API_KEY"))
                .modelName(modelName)
                .build();
    }

    @Bean
    EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore(
            @Value("${rag.openai.embedding-dimension}") int dimension,
            @Value("${rag.pinecone.cloud}") String cloud,
            @Value("${rag.pinecone.region}") String region,
            @Value("${rag.pinecone.namespace}") String namespace) {
        return PineconeEmbeddingStore.builder()
                .apiKey(requiredEnvironment("PINECONE_API_KEY"))
                .index(requiredEnvironment("PINECONE_INDEX_NAME"))
                .nameSpace(namespace)
                .createIndex(PineconeServerlessIndexConfig.builder()
                        .cloud(cloud)
                        .region(region)
                        .dimension(dimension)
                        .build())
                .build();
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
