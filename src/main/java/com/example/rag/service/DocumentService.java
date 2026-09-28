package com.example.rag.service;

import com.example.rag.exception.RagException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            @Value("${rag.chunk-size}") int chunkSize,
            @Value("${rag.chunk-overlap}") int chunkOverlap) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public Map<String, Object> upload(MultipartFile file) {
        validatePdf(file);
        String filename = file.getOriginalFilename() == null ? "uploaded.pdf" : file.getOriginalFilename();

        try {
            Document document = new ApachePdfBoxDocumentParser(true).parse(file.getInputStream());
            document.metadata().put("document", filename);
            document.metadata().put("filename", filename);

            List<TextSegment> segments = DocumentSplitters.recursive(chunkSize, chunkOverlap).split(document);
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("The PDF contains no usable text.");
            }

            for (TextSegment segment : segments) {
                embeddingStore.add(embeddingModel.embed(segment).content(), segment);
            }
            return Map.of("document", filename, "chunksStored", segments.size());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RagException("Could not parse, embed, or store the PDF.", exception);
        }
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The PDF file must not be empty.");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if (!"application/pdf".equalsIgnoreCase(contentType)
                && (filename == null || !filename.toLowerCase().endsWith(".pdf"))) {
            throw new IllegalArgumentException("Only PDF files are accepted.");
        }
    }
}
