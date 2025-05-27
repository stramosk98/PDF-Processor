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
        
        tesseract.setLanguage("eng"); 
        tesseract.setPageSegMode(1); 
        tesseract.setOcrEngineMode(1);
    }

    private void configureDataPath() {
        File tessDataDir = new File(TESSDATA_PATH);
        File engTrainedData = new File(tessDataDir, "eng.traineddata");
        
        if (!tessDataDir.exists() || !engTrainedData.exists()) {
            System.out.println("\n=== Tesseract Installation Required ===");
        } else {
            System.out.println("Using Tesseract data path: " + TESSDATA_PATH);
            tesseract.setDatapath(TESSDATA_PATH);
        }
    }

    public String extractTextFromImage(File imageFile) throws TesseractException {
        File engTrainedData = new File(TESSDATA_PATH, "eng.traineddata");
        return tesseract.doOCR(imageFile);
    }

    public static synchronized OCRProcessor getInstance() {
        if (instance == null) {
            instance = new OCRProcessor();
        }
        return instance;
    }
} 