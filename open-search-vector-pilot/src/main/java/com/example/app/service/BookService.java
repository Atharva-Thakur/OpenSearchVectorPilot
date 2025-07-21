package com.example.app.service;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.RestClient;
import org.opensearch.client.RequestOptions;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.common.xcontent.XContentType;
import org.apache.http.HttpHost;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.common.settings.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.embedding.EmbeddingModel;

@Service
public class BookService {
    private static final String INDEX = "vector-books-index";
    private static final int EMBEDDING_DIM = 1536;
    private static final String[] SEARCHABLE_FIELDS = {"author", "title"};
    private final RestHighLevelClient client;
    private final ObjectMapper mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                .build();
    @Autowired
    private EmbeddingModel embeddingModel; // Spring AI Azure OpenAI embedding model

    public BookService() {
        this.client = new RestHighLevelClient(
            RestClient.builder(new HttpHost("localhost", 9200, "http"))
        );
    }

    public String createFromFile(String filePath) {
        createIndexIfNotExists();
        try {
            byte[] jsonData = Files.readAllBytes(Paths.get(filePath));
            JsonNode rootNode = mapper.readTree(jsonData);
            if (rootNode.isArray()) {
                BulkRequest bulkRequest = new BulkRequest();
                int docId = 1;
                for (JsonNode node : rootNode) {
                    // Compose text for embedding
                    String text = (node.has("description") ? node.get("description").asText("") : "") +
                                  (node.has("title") ? " " + node.get("title").asText("") : "") +
                                  (node.has("author") ? " " + node.get("author").asText("") : "");
                    List<Float> embedding = getEmbedding(text);
                    com.fasterxml.jackson.databind.node.ArrayNode embeddingArray = mapper.createArrayNode();
                    for (Float v : embedding) {
                        embeddingArray.add(v);
                    }
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).set("embedding", embeddingArray);
                    IndexRequest indexRequest = new IndexRequest(INDEX)
                        .id(String.valueOf(docId++))
                        .source(mapper.writeValueAsString(node), XContentType.JSON);
                    bulkRequest.add(indexRequest);
                }
                BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                if (!bulkResponse.hasFailures()) {
                    return "Indexed " + (docId-1) + " documents from " + filePath;
                } else {
                    return "Bulk indexing had failures: " + bulkResponse.buildFailureMessage();
                }
            }
        } catch (IOException e) {
            return "Error indexing documents: " + e.getMessage();
        }
        return "No documents indexed.";
    }

    public String createDocument(String id, String json) {
        createIndexIfNotExists();
        try {
            JsonNode node = mapper.readTree(json);
            String text = (node.has("description") ? node.get("description").asText("") : "") +
                         (node.has("title") ? " " + node.get("title").asText("") : "") +
                         (node.has("author") ? " " + node.get("author").asText("") : "");
            List<Float> embedding = getEmbedding(text);
            System.out.println("Embedding for text: " + text + " is " + embedding);
            com.fasterxml.jackson.databind.node.ArrayNode embeddingArray = mapper.createArrayNode();
            for (Float v : embedding) {
                embeddingArray.add(v);
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).set("embedding", embeddingArray);
            IndexRequest request = new IndexRequest(INDEX).id(id).source(mapper.writeValueAsString(node), XContentType.JSON);
            IndexResponse response = client.index(request, RequestOptions.DEFAULT);
            return "Created document with id: " + response.getId();
        } catch (IOException e) {
            return "Error creating document: " + e.getMessage();
        }
    }

    public String readDocument(String id) {
        try {
            GetRequest request = new GetRequest(INDEX, id);
            GetResponse response = client.get(request, RequestOptions.DEFAULT);
            if (response.isExists()) {
                return response.getSourceAsString();
            } else {
                return "Document not found.";
            }
        } catch (IOException e) {
            return "Error reading document: " + e.getMessage();
        }
    }

    public String updateDocument(String id, String json) {
        try {
            JsonNode node = mapper.readTree(json);
            String text = (node.has("description") ? node.get("description").asText("") : "") +
                         (node.has("title") ? " " + node.get("title").asText("") : "") +
                         (node.has("author") ? " " + node.get("author").asText("") : "");
            List<Float> embedding = getEmbedding(text);
            com.fasterxml.jackson.databind.node.ArrayNode embeddingArray = mapper.createArrayNode();
            for (Float v : embedding) {
                embeddingArray.add(v);
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).set("embedding", embeddingArray);
            UpdateRequest request = new UpdateRequest(INDEX, id).doc(mapper.writeValueAsString(node), XContentType.JSON);
            client.update(request, RequestOptions.DEFAULT);
            return "Updated document with id: " + id;
        } catch (IOException e) {
            return "Error updating document: " + e.getMessage();
        }
    }

    // Helper method to get embedding using Spring AI
    private List<Float> getEmbedding(String text) {
        try {
            System.out.println("getEmbedding called with text: " + text);
            List<?> result = embeddingModel.embed(List.of(text));
            System.out.println("embeddingModel.embed result: " + result);
            if (result != null && !result.isEmpty()) {
                Object arr = result.get(0);
                System.out.println("First element of result: " + arr + ", type: " + (arr != null ? arr.getClass().getName() : "null"));
                if (arr instanceof float[]) {
                    float[] floatArr = (float[]) arr;
                    System.out.println("float[] length: " + floatArr.length);
                    List<Float> floatList = new ArrayList<>(floatArr.length);
                    for (float v : floatArr) floatList.add(v);
                    System.out.println("Returning floatList: " + floatList);
                    return floatList;
                } else if (arr instanceof List<?>) {
                    // Some models return List<Double>
                    List<?> list = (List<?>) arr;
                    System.out.println("List<?> size: " + list.size());
                    List<Float> floatList = new ArrayList<>(list.size());
                    for (Object v : list) floatList.add(((Number) v).floatValue());
                    System.out.println("Returning floatList: " + floatList);
                    return floatList;
                } else {
                    System.out.println("Unknown embedding type: " + arr.getClass().getName());
                }
            } else {
                System.out.println("Embedding result is null or empty");
            }
        } catch (Exception e) {
            System.out.println("Exception in getEmbedding: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Returning empty embedding list");
        return Collections.emptyList();
    }

    public String deleteDocument(String id) {
        try {
            DeleteRequest request = new DeleteRequest(INDEX, id);
            DeleteResponse response = client.delete(request, RequestOptions.DEFAULT);
            return "Deleted document with id: " + response.getId();
        } catch (IOException e) {
            return "Error deleting document: " + e.getMessage();
        }
    }

    public List<String> listDocuments() {
        List<String> docs = new ArrayList<>();
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchAllQuery());
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                docs.add(hit.getId() + ": " + hit.getSourceAsString());
            }
        } catch (IOException e) {
            docs.add("Error listing documents: " + e.getMessage());
        }
        return docs;
    }

    public List<String> searchBooks(String field, String value) {
        List<String> results = new ArrayList<>();
        if (!isSearchableField(field)) {
            results.add("Invalid field. Use 'author' or 'title'.");
            return results;
        }
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchQuery(field, value));
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                results.add(hit.getId() + ": " + hit.getSourceAsString());
            }
        } catch (IOException e) {
            results.add("Error searching books: " + e.getMessage());
        }
        return results;
    }

    public List<String> vectorSearchBooks(String query) {
        List<String> results = new ArrayList<>();
        try {
            // Get embedding as float[]
            float[] embeddingArray = (float[]) embeddingModel.embed(List.of(query)).get(0);
            // Build k-NN query
            Map<String, Object> knnQuery = new HashMap<>();
            Map<String, Object> embeddingQuery = new HashMap<>();
            List<Float> embeddingList = new ArrayList<>(embeddingArray.length);
            for (float v : embeddingArray) {
                embeddingList.add(v);
            }
            embeddingQuery.put("vector", embeddingList);
            embeddingQuery.put("k", 5);
            knnQuery.put("embedding", embeddingQuery);
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("knn", knnQuery);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.size(5);
            searchSourceBuilder.query(new org.opensearch.index.query.WrapperQueryBuilder(new ObjectMapper().writeValueAsString(queryMap)));
            SearchRequest searchRequest = new SearchRequest(INDEX);
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                results.add(hit.getId() + ": " + hit.getSourceAsString());
            }
        } catch (Exception e) {
            results.add("Error in vector search: " + e.getMessage());
        }
        return results;
    }

    private boolean isSearchableField(String field) {
        for (String f : SEARCHABLE_FIELDS) {
            if (f.equals(field)) return true;
        }
        return false;
    }

    private void createIndexIfNotExists() {
        try {
            GetIndexRequest getIndexRequest = new GetIndexRequest(INDEX);
            boolean exists = client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
            if (!exists) {
                CreateIndexRequest request = new CreateIndexRequest(INDEX);
                request.settings(Settings.builder()
                    .put("index.knn", true) // Enable k-NN for vector search
                );
                Map<String, Object> properties = new HashMap<>();
                properties.put("book_id", Collections.singletonMap("type", "integer"));
                properties.put("title", Collections.singletonMap("type", "text"));
                properties.put("author", Collections.singletonMap("type", "text"));
                properties.put("language", Collections.singletonMap("type", "keyword"));
                properties.put("average_rating", Collections.singletonMap("type", "float"));
                properties.put("ratings_count", Collections.singletonMap("type", "integer"));
                properties.put("publication_date", Collections.singletonMap("type", "keyword"));
                properties.put("format", Collections.singletonMap("type", "keyword"));
                properties.put("publisher", Collections.singletonMap("type", "keyword"));
                properties.put("description", Collections.singletonMap("type", "text"));
                properties.put("image_url", Collections.singletonMap("type", "keyword"));
                properties.put("shelves", Collections.singletonMap("type", "keyword"));
                Map<String, Object> embedding = new HashMap<>();
                embedding.put("type", "knn_vector");
                embedding.put("dimension", EMBEDDING_DIM);
                embedding.put("index", true); // Enable indexing for vector field
                embedding.put("similarity", "cosine"); // Use cosine similarity
                properties.put("embedding", embedding);
                Map<String, Object> mapping = new HashMap<>();
                mapping.put("properties", properties);
                request.mapping(mapper.writeValueAsString(mapping), XContentType.JSON);
                CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
            }
        } catch (IOException e) {
            // log error
        }
    }
}
