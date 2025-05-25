package com.pdfprocessor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.sourceforge.tess4j.TesseractException;

public class PDFServer {
    private final int port;
    private final ExecutorService executorService;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public PDFServer(int port) {
        this.port = port;
        this.executorService = Executors.newFixedThreadPool(
            Config.getInstance().getMaxConcurrentThreads()
        );
    }

    public void start() {
        running = true;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected from " + clientSocket.getInetAddress());
                    executorService.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.out.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error starting server: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {
            
            PDFProtocol.SearchRequest request = (PDFProtocol.SearchRequest) in.readObject();
            
            // Create temporary file
            Path tempFile = Files.createTempFile("pdf_", ".pdf");
            Files.write(tempFile, request.getPdfContent());
            
            try {
                OCRProcessor processor = OCRProcessor.getInstance();
                String extractedText = processor.extractTextFromImage(tempFile.toFile());
                String formattedText = extractedText.replaceAll("\\r|\\n", " ").toLowerCase();
                String searchPhrase = request.getSearchPhrase().toLowerCase();
                
                if (formattedText.contains(searchPhrase)) {
                    int index = formattedText.indexOf(searchPhrase);
                    String context = extractContext(formattedText, index, searchPhrase);
                    out.writeObject(new PDFProtocol.SearchResponse(true, context, null));
                } else {
                    out.writeObject(new PDFProtocol.SearchResponse(false, null, null));
                }
            } catch (TesseractException e) {
                System.out.println("Error processing PDF: " + e.getMessage());
                out.writeObject(new PDFProtocol.SearchResponse(false, null, e.getMessage()));
            } finally {
                Files.deleteIfExists(tempFile);
            }
            
        } catch (Exception e) {
            System.out.println("Error handling client request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    private String extractContext(String text, int matchIndex, String searchPhrase) {
        int contextSize = 50;
        int start = Math.max(0, matchIndex - contextSize);
        int end = Math.min(text.length(), matchIndex + searchPhrase.length() + contextSize);
        return "..." + text.substring(start, end) + "...";
    }

    public void shutdown() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing server socket: " + e.getMessage());
            }
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        PDFServer server = new PDFServer(PDFProtocol.DEFAULT_PORT);
        server.start();
    }
} 