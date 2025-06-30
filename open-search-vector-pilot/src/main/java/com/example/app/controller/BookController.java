package com.example.app.controller;

import com.example.app.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
public class BookController {
    @Autowired
    private BookService bookService;

    @PostMapping("/bulk")
    public ResponseEntity<?> bulkCreate(@RequestParam String filePath) {
        return ResponseEntity.ok(bookService.createFromFile(filePath));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestParam String id, @RequestBody String json) {
        return ResponseEntity.ok(bookService.createDocument(id, json));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> read(@PathVariable String id) {
        return ResponseEntity.ok(bookService.readDocument(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody String json) {
        return ResponseEntity.ok(bookService.updateDocument(id, json));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        return ResponseEntity.ok(bookService.deleteDocument(id));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(bookService.listDocuments());
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String field, @RequestParam String value) {
        return ResponseEntity.ok(bookService.searchBooks(field, value));
    }

    @GetMapping("/vector-search")
    public ResponseEntity<?> vectorSearch(@RequestParam String query) {
        return ResponseEntity.ok(bookService.vectorSearchBooks(query));
    }
}
