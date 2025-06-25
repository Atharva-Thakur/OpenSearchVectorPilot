package com.example.app;

import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.RestClient;
import org.opensearch.client.RequestOptions;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.apache.http.HttpHost;
import org.opensearch.common.xcontent.XContentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.util.HashMap;
import java.util.Map;

public class PdfIngestAndSearch {
    private static final String INDEX = "pdf-index";
    private final RestHighLevelClient client;

    public PdfIngestAndSearch() {
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

    public void ingestPdf(String pdfPath) {
        try (PDDocument document = PDDocument.load(Files.newInputStream(Paths.get(pdfPath)))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("content", text);
            IndexRequest request = new IndexRequest(INDEX)
                .source(jsonMap, XContentType.JSON);
            IndexResponse response = client.index(request, RequestOptions.DEFAULT);
            System.out.println("PDF content indexed with id: " + response.getId());
        } catch (IOException e) {
            System.err.println("Error ingesting PDF: " + e.getMessage());
        }
    }

    public void searchPdfContent(String query) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchQuery("content", query));
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            System.out.println("Search results for query: '" + query + "':");
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                System.out.println(hit.getId() + ": " + hit.getSourceAsString());
            }
        } catch (IOException e) {
            System.err.println("Error searching PDF content: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        PdfIngestAndSearch pdfApp = new PdfIngestAndSearch();
        try {
            // Ingest the PDF file
            pdfApp.ingestPdf("dummy.pdf");
            // Example search in the PDF content
            pdfApp.searchPdfContent("precipice");
        } finally {
            pdfApp.close();
        }
    }
}
