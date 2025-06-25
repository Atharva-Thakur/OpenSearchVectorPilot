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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class BookCrudApp {
    private static final String INDEX = "books-index";
    private final RestHighLevelClient client;
    private final ObjectMapper mapper = new ObjectMapper();

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

    public void createFromFile(String filePath) {
        try {
            byte[] jsonData = Files.readAllBytes(Paths.get(filePath));
            JsonNode rootNode = mapper.readTree(jsonData);
            if (rootNode.isArray()) {
                int docId = 1;
                for (JsonNode node : rootNode) {
                    IndexRequest indexRequest = new IndexRequest(INDEX)
                        .id(String.valueOf(docId++))
                        .source(mapper.writeValueAsString(node), XContentType.JSON);
                    client.index(indexRequest, RequestOptions.DEFAULT);
                }
                System.out.println("Indexed " + (docId-1) + " documents from " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error indexing documents: " + e.getMessage());
        }
    }

    public void createDocument(String id, String json) {
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

    public void runCrudMenu() {
        Scanner scanner = new Scanner(System.in);
        try {
            while (true) {
                System.out.println("\nCRUD Menu:");
                System.out.println("1. Bulk create from data.json");
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
                        createFromFile("data.json");
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
                        if (!field.equals("author") && !field.equals("title")) {
                            System.out.println("Invalid field. Use 'author' or 'title'.");
                            break;
                        }
                        System.out.print("Enter search value: ");
                        String value = scanner.nextLine();
                        searchBooks(field, value);
                        break;
                    case "0":
                        System.out.println("Exiting...");
                        return;
                    default:
                        System.out.println("Invalid option.");
                }
            }
        } finally {
            scanner.close();
        }
    }
}
