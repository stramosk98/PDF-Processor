package com.pdfprocessor.server;

import java.io.File;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class OCRProcessor {
    private static OCRProcessor instance;
    private final Tesseract tesseract;
    private static final String TESSDATA_PATH = "C:\\Program Files\\Tesseract-OCR\\tessdata";

    private OCRProcessor() {
        tesseract = new Tesseract();
        configureDataPath();
        
        // Configure Tesseract
        tesseract.setLanguage("eng"); // Set to English
        tesseract.setPageSegMode(1); // Automatic page segmentation with OSD
        tesseract.setOcrEngineMode(1); // Neural nets LSTM engine
    }

    private void configureDataPath() {
        File tessDataDir = new File(TESSDATA_PATH);
        File engTrainedData = new File(tessDataDir, "eng.traineddata");
        
        if (!tessDataDir.exists() || !engTrainedData.exists()) {
            System.out.println("\n=== Tesseract Installation Required ===");
            System.out.println("Please follow these steps to install Tesseract:");
            System.out.println("1. Download Tesseract installer from: https://github.com/UB-Mannheim/tesseract/wiki");
            System.out.println("2. Run the installer (choose 64-bit version)");
            System.out.println("3. Install to the default location (C:\\Program Files\\Tesseract-OCR)");
            System.out.println("4. Download eng.traineddata from: https://github.com/tesseract-ocr/tessdata");
            System.out.println("5. Place eng.traineddata in: " + TESSDATA_PATH);
            System.out.println("6. Restart the server");
            System.out.println("=======================================\n");
        } else {
            System.out.println("Using Tesseract data path: " + TESSDATA_PATH);
            tesseract.setDatapath(TESSDATA_PATH);
        }
    }

    public String extractTextFromImage(File imageFile) throws TesseractException {
        File engTrainedData = new File(TESSDATA_PATH, "eng.traineddata");
        if (!engTrainedData.exists()) {
            throw new TesseractException("Tesseract is not properly configured. Please check server logs for installation instructions.");
        }
        return tesseract.doOCR(imageFile);
    }

    public static synchronized OCRProcessor getInstance() {
        if (instance == null) {
            instance = new OCRProcessor();
        }
        return instance;
    }
} 