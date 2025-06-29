package com.example.app;

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
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.common.settings.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import java.util.Scanner;

public class BookCrudApp {
    private static final String INDEX = "vector-books-index";
    private static final int EMBEDDING_DIM = 384;
    private static final String[] SEARCHABLE_FIELDS = {"author", "title"};
    private final RestHighLevelClient client;
    private final ObjectMapper mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                .build();

    public BookCrudApp() {
        this.client = new RestHighLevelClient(
            RestClient.builder(new HttpHost("localhost", 9200, "http"))
        );
    }

    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            System.err.println("Error closing client: " + e.getMessage());
        }
    }

    public void createIndexIfNotExists() {
        try {
            GetIndexRequest getIndexRequest = new GetIndexRequest(INDEX);
            boolean exists = client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
            if (!exists) {
                CreateIndexRequest request = new CreateIndexRequest(INDEX);
                request.settings(Settings.builder()
                    .put("index.knn", true)
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
                properties.put("embedding", embedding);
                Map<String, Object> mapping = new HashMap<>();
                mapping.put("properties", properties);
                request.mapping(mapper.writeValueAsString(mapping), XContentType.JSON);
                CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
                if (createIndexResponse.isAcknowledged()) {
                    System.out.println("Index '" + INDEX + "' created with vector mapping.");
                }
            }
        } catch (IOException e) {
            System.err.println("Error creating index: " + e.getMessage());
        }
    }

    public void createFromFile(String filePath) {
        createIndexIfNotExists();
        try {
            byte[] jsonData = Files.readAllBytes(Paths.get(filePath));
            JsonNode rootNode = mapper.readTree(jsonData);
            if (rootNode.isArray()) {
                BulkRequest bulkRequest = new BulkRequest();
                int docId = 1;
                for (JsonNode node : rootNode) {
                    IndexRequest indexRequest = new IndexRequest(INDEX)
                        .id(String.valueOf(docId++))
                        .source(mapper.writeValueAsString(node), XContentType.JSON);
                    bulkRequest.add(indexRequest);
                }
                BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                if (!bulkResponse.hasFailures()) {
                    System.out.println("Indexed " + (docId-1) + " documents from " + filePath);
                } else {
                    System.err.println("Bulk indexing had failures: " + bulkResponse.buildFailureMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error indexing documents: " + e.getMessage());
        }
    }

    public void createDocument(String id, String json) {
        createIndexIfNotExists();
        try {
            IndexRequest request = new IndexRequest(INDEX).id(id).source(json, XContentType.JSON);
            IndexResponse response = client.index(request, RequestOptions.DEFAULT);
            System.out.println("Created document with id: " + response.getId());
        } catch (IOException e) {
            System.err.println("Error creating document: " + e.getMessage());
        }
    }

    public void readDocument(String id) {
        try {
            GetRequest request = new GetRequest(INDEX, id);
            GetResponse response = client.get(request, RequestOptions.DEFAULT);
            if (response.isExists()) {
                System.out.println("Document: " + response.getSourceAsString());
            } else {
                System.out.println("Document not found.");
            }
        } catch (IOException e) {
            System.err.println("Error reading document: " + e.getMessage());
        }
    }

    public void updateDocument(String id, String json) {
        try {
            UpdateRequest request = new UpdateRequest(INDEX, id).doc(json, XContentType.JSON);
            client.update(request, RequestOptions.DEFAULT);
            System.out.println("Updated document with id: " + id);
        } catch (IOException e) {
            System.err.println("Error updating document: " + e.getMessage());
        }
    }

    public void deleteDocument(String id) {
        try {
            DeleteRequest request = new DeleteRequest(INDEX, id);
            DeleteResponse response = client.delete(request, RequestOptions.DEFAULT);
            System.out.println("Deleted document with id: " + response.getId());
        } catch (IOException e) {
            System.err.println("Error deleting document: " + e.getMessage());
        }
    }

    public void listDocuments() {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchAllQuery());
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            System.out.println("All documents in " + INDEX + ":");
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                System.out.println(hit.getId() + ": " + hit.getSourceAsString());
            }
        } catch (IOException e) {
            System.err.println("Error listing documents: " + e.getMessage());
        }
    }

    public void searchBooks(String field, String value) {
        if (!isSearchableField(field)) {
            System.out.println("Invalid field. Use 'author' or 'title'.");
            return;
        }
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchQuery(field, value));
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            System.out.println("Search results for " + field + " = '" + value + "':");
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                System.out.println(hit.getId() + ": " + hit.getSourceAsString());
            }
        } catch (IOException e) {
            System.err.println("Error searching books: " + e.getMessage());
        }
    }

    private boolean isSearchableField(String field) {
        for (String f : SEARCHABLE_FIELDS) {
            if (f.equals(field)) return true;
        }
        return false;
    }

    public void vectorSearch(float[] queryVector, int k) {
        if (queryVector == null || queryVector.length != EMBEDDING_DIM) {
            System.err.println("Query vector must be of length " + EMBEDDING_DIM);
            return;
        }
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            StringBuilder vectorBuilder = new StringBuilder("[");
            for (int i = 0; i < queryVector.length; i++) {
                vectorBuilder.append(queryVector[i]);
                if (i < queryVector.length - 1) vectorBuilder.append(",");
            }
            vectorBuilder.append("]");
            String knnQuery = "{" +
                "  \"knn\": {" +
                "    \"embedding\": {" +
                "      \"vector\": " + vectorBuilder.toString() + "," +
                "      \"k\": " + k +
                "    }" +
                "  }" +
                "}";
            searchSourceBuilder.query(new org.opensearch.index.query.WrapperQueryBuilder(knnQuery));
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            System.out.println("Vector search results:");
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                System.out.println(hit.getId() + ": " + hit.getSourceAsString());
            }
        } catch (IOException e) {
            System.err.println("Error in vector search: " + e.getMessage());
        }
    }

    public void runCrudMenu() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nCRUD Menu:");
                System.out.println("1. Bulk create from books.json");
                System.out.println("2. Create document");
                System.out.println("3. Read document");
                System.out.println("4. Update document");
                System.out.println("5. Delete document");
                System.out.println("6. List all documents");
                System.out.println("7. Search books");
                System.out.println("0. Exit");
                System.out.print("Choose an option: ");
                String choice = scanner.nextLine();
                switch (choice) {
                    case "1":
                        createFromFile("books.json");
                        break;
                    case "2":
                        System.out.print("Enter document id: ");
                        String createId = scanner.nextLine();
                        System.out.print("Enter JSON document: ");
                        String createJson = scanner.nextLine();
                        createDocument(createId, createJson);
                        break;
                    case "3":
                        System.out.print("Enter document id: ");
                        String readId = scanner.nextLine();
                        readDocument(readId);
                        break;
                    case "4":
                        System.out.print("Enter document id: ");
                        String updateId = scanner.nextLine();
                        System.out.print("Enter updated JSON: ");
                        String updateJson = scanner.nextLine();
                        updateDocument(updateId, updateJson);
                        break;
                    case "5":
                        System.out.print("Enter document id: ");
                        String deleteId = scanner.nextLine();
                        deleteDocument(deleteId);
                        break;
                    case "6":
                        listDocuments();
                        break;
                    case "7":
                        System.out.print("Search by (author/title): ");
                        String field = scanner.nextLine();
                        System.out.print("Enter search value: ");
                        String value = scanner.nextLine();
                        searchBooks(field, value);
                        break;
                    case "0":
                        System.out.println("Exiting...");
                        close();
                        return;
                    default:
                        System.out.println("Invalid option.");
                }
            }
        }
    }
}
