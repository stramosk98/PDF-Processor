package com.pdfprocessor;

public class Config {
    private static final Config INSTANCE = new Config();
    
    private String filesDirectory = "src/main/resources";
    private String tesseractPath = "C:\\Program Files\\Tesseract-OCR\\tessdata";
    private String language = "eng";
    private int maxConcurrentThreads = 2;
    
    private Config() {}
    
    public static Config getInstance() {
        return INSTANCE;
    }
    
    public String getFilesDirectory() {
        return filesDirectory;
    }
    
    public void setFilesDirectory(String filesDirectory) {
        this.filesDirectory = filesDirectory;
    }
    
    public String getTesseractPath() {
        return tesseractPath;
    }
    
    public void setTesseractPath(String tesseractPath) {
        this.tesseractPath = tesseractPath;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public int getMaxConcurrentThreads() {
        return maxConcurrentThreads;
    }
    
    public void setMaxConcurrentThreads(int maxConcurrentThreads) {
        this.maxConcurrentThreads = maxConcurrentThreads;
    }
} 