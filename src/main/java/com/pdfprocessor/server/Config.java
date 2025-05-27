package com.pdfprocessor.server;

public class Config {
    private static Config instance;
    private final int maxConcurrentThreads;
    private final String filesDirectory;

    private Config() {
        this.maxConcurrentThreads = Runtime.getRuntime().availableProcessors();
        this.filesDirectory = "pdfs";
    }

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    public int getMaxConcurrentThreads() {
        return maxConcurrentThreads;
    }

    public String getFilesDirectory() {
        return filesDirectory;
    }
} 