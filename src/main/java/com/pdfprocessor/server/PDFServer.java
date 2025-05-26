package com.pdfprocessor.server;

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

import com.pdfprocessor.shared.PDFProtocol;

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
        try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
            
            PDFProtocol.SearchRequest request = (PDFProtocol.SearchRequest) in.readObject();
            
            // Validate request
            if (request == null || request.getPdfContent() == null || request.getSearchPhrase() == null) {
                out.writeObject(new PDFProtocol.SearchResponse(false, null, "Invalid request data"));
                out.flush();
                return;
            }

            if (request.getPdfContent().length > PDFProtocol.MAX_FILE_SIZE) {
                out.writeObject(new PDFProtocol.SearchResponse(false, null, 
                    "PDF file too large. Maximum size is " + (PDFProtocol.MAX_FILE_SIZE / (1024 * 1024)) + "MB"));
                out.flush();
                return;
            }
            
            // Create temporary file
            Path tempFile = Files.createTempFile("pdf_", ".pdf");
            try {
                Files.write(tempFile, request.getPdfContent());
                
                OCRProcessor processor = OCRProcessor.getInstance();
                String extractedText = processor.extractTextFromImage(tempFile.toFile());
                String formattedText = extractedText.replaceAll("\\r|\\n", " ").toLowerCase();
                String searchPhrase = request.getSearchPhrase().toLowerCase();
                
                PDFProtocol.SearchResponse response;
                if (formattedText.contains(searchPhrase)) {
                    int index = formattedText.indexOf(searchPhrase);
                    String context = extractContext(formattedText, index, searchPhrase);
                    response = new PDFProtocol.SearchResponse(true, context, null);
                } else {
                    response = new PDFProtocol.SearchResponse(false, null, null);
                }
                
                out.writeObject(response);
                out.flush();
                
            } catch (TesseractException e) {
                System.out.println("Error processing PDF: " + e.getMessage());
                out.writeObject(new PDFProtocol.SearchResponse(false, null, "Error processing PDF: " + e.getMessage()));
                out.flush();
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    System.out.println("Error deleting temporary file: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.out.println("Error handling client request: " + e.getMessage());
            e.printStackTrace();
            try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {
                out.writeObject(new PDFProtocol.SearchResponse(false, null, "Server error: " + e.getMessage()));
                out.flush();
            } catch (IOException ex) {
                System.out.println("Error sending error response to client: " + ex.getMessage());
            }
        } finally {
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
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