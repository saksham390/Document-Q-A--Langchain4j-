package com.example.rag.service;

import com.example.rag.exception.RagException;
import com.example.rag.model.AskResponse;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private static final String PROMPT = "You are a document question-answering assistant.\n"
            + "Answer the user's question using only the provided context.\n"
            + "Do not invent information.\n"
            + "If the answer cannot be found in the context, clearly say that the information is not available in the provided document.\n\n"
            + "Context:\n%s\n\nQuestion:\n%s";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel chatModel;
    private final int topK;

    public RagService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatModel chatModel,
            @Value("${rag.retrieval.top-k}") int topK) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatModel = chatModel;
        this.topK = topK;
    }

    public AskResponse ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("The question must not be empty.");
        }

        try {
            var questionEmbedding = embeddingModel.embed(question).content();
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(questionEmbedding)
                            .maxResults(topK)
                            .build());

            List<EmbeddingMatch<TextSegment>> matches = result.matches();
            String context = matches.stream()
                    .map(match -> match.embedded().text())
                    .reduce((left, right) -> left + "\n\n---\n\n" + right)
                    .orElse("No relevant context was found.");

            String answer = chatModel.chat(PROMPT.formatted(context, question));
            List<AskResponse.Source> sources = matches.stream()
                    .map(EmbeddingMatch::embedded)
                    .map(TextSegment::metadata)
                    .map(metadata -> new AskResponse.Source(
                            firstMetadata(metadata, "filename", "document"),
                            pageNumber(metadata.getString("page_number"))))
                    .distinct()
                    .toList();
            return new AskResponse(answer, sources);
        } catch (Exception exception) {
            throw new RagException("Could not embed the question, search Pinecone, or generate an answer.", exception);
        }
    }

    private String firstMetadata(dev.langchain4j.data.document.Metadata metadata, String first, String second) {
        String value = metadata.getString(first);
        return value == null ? metadata.getString(second) : value;
    }

    private Integer pageNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
