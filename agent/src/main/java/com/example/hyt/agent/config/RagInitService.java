package com.example.hyt.agent.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class RagInitService {

    // 1. 声明向量库和向量化模型（这里用本地免费模型）
    private final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    // 2. 暴露获取向量库的方法，后面问答要用
    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    // 暴露获取向量模型的方法
    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    // 3. 项目启动时自动执行：加载文档并入库
    @PostConstruct
    public void init() throws URISyntaxException {
        // 检查向量库是否已有数据，避免重复初始化
        if (!embeddingStore.search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("test").content())
                .maxResults(1)
                .build()).matches().isEmpty()) {
            System.out.println("✅ RAG 知识库已存在，跳过初始化");
            return;
        }

        Path docsPath;
        try {
            docsPath = Paths.get(getClass().getClassLoader().getResource("docs").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to load document path", e);
        }

        // 加载文档 -> 按段落切分（规则类文档按编号分割） -> 向量化 -> 存入内存库
        try {
            List<Document> documents = dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments(docsPath);
            for (Document doc : documents) {
                // 按数字编号分割（如 "1. xxx" 或 "2. xxx"）
                String text = doc.text();
                String[] paragraphs = text.split("(?=\\n\\d+\\.)");

                for (String paragraph : paragraphs) {
                    String trimmed = paragraph.trim();
                    if (!trimmed.isEmpty()) {
                        TextSegment segment = new TextSegment(trimmed, doc.metadata());
                        embeddingStore.add(embeddingModel.embed(segment).content(), segment);
                    }
                }
            }
            System.out.println("✅ RAG 知识库初始化完成！共加载 " + documents.size() + " 个文档");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RAG knowledge base", e);
        }
    }
}