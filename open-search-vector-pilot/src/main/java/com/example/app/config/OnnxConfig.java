package com.example.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import java.util.Map;

@Configuration
public class OnnxConfig {
    @Bean
    public EmbeddingModel embeddingModel() throws Exception {
        TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
        embeddingModel.setTokenizerResource("classpath:/models/tokenizer.json");
        embeddingModel.setModelResource("classpath:/models/model.onnx");
        embeddingModel.setResourceCacheDirectory(System.getProperty("java.io.tmpdir") + "/spring-ai-onnx-model");
        embeddingModel.setTokenizerOptions(Map.of("padding", "true"));
        embeddingModel.afterPropertiesSet();
        return embeddingModel;
    }
}
