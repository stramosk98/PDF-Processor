package com.pdfprocessor.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        System.out.println("Handling client connection from: " + clientSocket.getInetAddress());
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        
        try {
            // Configure socket
            clientSocket.setSoTimeout(60000); // 60 seconds timeout
            clientSocket.setTcpNoDelay(true);
            clientSocket.setKeepAlive(true);
            
            System.out.println("Creating output stream...");
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            out.flush(); // Important: flush the header
            
            System.out.println("Creating input stream...");
            in = new ObjectInputStream(clientSocket.getInputStream());
            
            System.out.println("Reading request object...");
            Object requestObj = in.readObject();
            
            if (!(requestObj instanceof PDFProtocol.SearchRequest)) {
                System.out.println("Invalid request type: " + (requestObj != null ? requestObj.getClass().getName() : "null"));
                sendErrorResponse(out, "Invalid request type");
                return;
            }
            
            PDFProtocol.SearchRequest request = (PDFProtocol.SearchRequest) requestObj;
            System.out.println("Processing search request for file: " + request.getFileName() + 
                             ", search phrase: '" + request.getSearchText() + "'");
            
            // Validate request
            if (request.getPdfContent() == null || request.getSearchText() == null) {
                System.out.println("Invalid request data - null content or search phrase");
                sendErrorResponse(out, "Invalid request data");
                return;
            }

            if (request.getPdfContent().length > PDFProtocol.MAX_FILE_SIZE) {
                System.out.println("PDF file too large: " + request.getPdfContent().length + " bytes");
                sendErrorResponse(out, "PDF file too large. Maximum size is " + 
                    (PDFProtocol.MAX_FILE_SIZE / (1024 * 1024)) + "MB");
                return;
            }
            
            // Create temporary file
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("pdf_", ".pdf");
                System.out.println("Created temporary file: " + tempFile);
                
                System.out.println("Writing PDF content to temporary file...");
                Files.write(tempFile, request.getPdfContent());
                
                System.out.println("Processing PDF with OCR...");
                OCRProcessor processor = OCRProcessor.getInstance();
                String extractedText = processor.extractTextFromImage(tempFile.toFile());
                
                if (extractedText == null || extractedText.isEmpty()) {
                    System.out.println("OCR produced no text output");
                    sendErrorResponse(out, "No text could be extracted from the PDF");
                    return;
                }
                
                String formattedText = extractedText.replaceAll("\\r|\\n", " ").toLowerCase();
                String searchPhrase = request.getSearchText().toLowerCase();
                
                System.out.println("Searching for phrase: '" + searchPhrase + "'");
                PDFProtocol.SearchResponse response;
                
                List<String> contexts = new ArrayList<>();
                int lastIndex = 0;
                while ((lastIndex = formattedText.indexOf(searchPhrase, lastIndex)) != -1) {
                    String context = extractContext(formattedText, lastIndex, searchPhrase);
                    contexts.add(context);
                    lastIndex += searchPhrase.length();
                }
                
                if (!contexts.isEmpty()) {
                    response = new PDFProtocol.SearchResponse(true, contexts, null);
                    System.out.println("Found " + contexts.size() + " matches");
                } else {
                    response = new PDFProtocol.SearchResponse(false, null, null);
                    System.out.println("No matches found");
                }
                
                if (!clientSocket.isClosed() && clientSocket.isConnected()) {
                    System.out.println("Sending response to client...");
                    out.writeObject(response);
                    out.flush();
                    System.out.println("Response sent successfully");
                } else {
                    System.out.println("Client connection lost before sending response");
                }
                
            } catch (TesseractException e) {
                System.out.println("Tesseract error: " + e.getMessage());
                e.printStackTrace();
                sendErrorResponse(out, "Error processing PDF: " + e.getMessage());
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                        System.out.println("Temporary file deleted");
                    } catch (IOException e) {
                        System.out.println("Error deleting temporary file: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            System.out.println("Error handling client request: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(out, "Server error: " + e.getMessage());
        } finally {
            System.out.println("Cleaning up connection...");
            // Close streams
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    System.out.println("Error closing output stream: " + e.getMessage());
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    System.out.println("Error closing input stream: " + e.getMessage());
                }
            }
            // Close socket
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing client socket: " + e.getMessage());
            }
            System.out.println("Connection cleanup completed");
        }
    }

    private void sendErrorResponse(ObjectOutputStream out, String errorMessage) {
        if (out != null) {
            try {
                System.out.println("Sending error response: " + errorMessage);
                out.writeObject(new PDFProtocol.SearchResponse(false, null, errorMessage));
                out.flush();
            } catch (IOException e) {
                System.out.println("Error sending error response: " + e.getMessage());
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