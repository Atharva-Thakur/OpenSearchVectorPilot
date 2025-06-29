package com.example.app;

public class App {
    public static void main(String[] args) {
        BookCrudApp crudApp = new BookCrudApp();
        try {
            crudApp.runCrudMenu();
        } finally {
            crudApp.close();
        }
    }
}
