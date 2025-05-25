package com.pdfprocessor.server;

import java.io.File;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class OCRProcessor {
    private static OCRProcessor instance;
    private final Tesseract tesseract;

    private OCRProcessor() {
        tesseract = new Tesseract();
        // Configure Tesseract if needed
        // tesseract.setDatapath("path/to/tessdata");
    }

    public static synchronized OCRProcessor getInstance() {
        if (instance == null) {
            instance = new OCRProcessor();
        }
        return instance;
    }

    public String extractTextFromImage(File imageFile) throws TesseractException {
        return tesseract.doOCR(imageFile);
    }
} 