package com.pdfprocessor.server;

import java.io.File;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class OCRProcessor {
    private static OCRProcessor instance;
    private final Tesseract tesseract;
    private String dataPath;
    private static final String[] POSSIBLE_DATA_PATHS = {
        "/usr/share/tesseract-ocr/4.00/tessdata",  // Ubuntu 20.04+
        "/usr/share/tesseract-ocr/tessdata",       // Ubuntu older versions
        "/usr/local/share/tessdata",               // Custom installation
        "/opt/homebrew/share/tessdata"             // MacOS Homebrew
    };

    private OCRProcessor() {
        tesseract = new Tesseract();
        configureDataPath();
        
        // Configure Tesseract
        tesseract.setLanguage("eng"); // Set to English
        tesseract.setPageSegMode(1); // Automatic page segmentation with OSD
        tesseract.setOcrEngineMode(1); // Neural nets LSTM engine
    }

    private void configureDataPath() {
        String configuredPath = null;
        
        // First check if TESSDATA_PREFIX environment variable is set
        String envPath = System.getenv("TESSDATA_PREFIX");
        if (envPath != null && new File(envPath).exists()) {
            configuredPath = envPath;
            System.out.println("Using Tesseract data path from environment: " + envPath);
        } else {
            // Try each possible path
            for (String path : POSSIBLE_DATA_PATHS) {
                if (new File(path).exists()) {
                    configuredPath = path;
                    System.out.println("Found Tesseract data path: " + path);
                    break;
                }
            }
        }

        if (configuredPath != null) {
            tesseract.setDatapath(configuredPath);
            this.dataPath = configuredPath;
        } else {
            System.out.println("WARNING: Could not find Tesseract data path. Please ensure Tesseract is properly installed.");
        }
    }

    public static synchronized OCRProcessor getInstance() {
        if (instance == null) {
            instance = new OCRProcessor();
        }
        return instance;
    }

    public String extractTextFromImage(File imageFile) throws TesseractException {
        if (!imageFile.exists()) {
            throw new TesseractException("PDF file does not exist: " + imageFile.getAbsolutePath());
        }
        
        try {
            System.out.println("Starting OCR processing for file: " + imageFile.getName());
            System.out.println("Using Tesseract data path: " + this.dataPath);
            
            String result = tesseract.doOCR(imageFile);
            
            if (result == null || result.trim().isEmpty()) {
                System.out.println("Warning: OCR produced no text output");
            } else {
                System.out.println("OCR processing completed successfully");
            }
            
            return result;
            
        } catch (TesseractException e) {
            System.out.println("Tesseract OCR error: " + e.getMessage());
            if (this.dataPath == null || !new File(this.dataPath).exists()) {
                throw new TesseractException("Tesseract data path not found. Please ensure Tesseract is properly installed on the server.");
            }
            throw e;
        }
    }
} 