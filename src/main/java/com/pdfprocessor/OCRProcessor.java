package com.pdfprocessor;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class OCRProcessor {
    private static final Logger logger = LoggerFactory.getLogger(OCRProcessor.class);
    private final Tesseract tesseract;
    private static final OCRProcessor INSTANCE = new OCRProcessor();

    private OCRProcessor() {
        Config config = Config.getInstance();
        tesseract = new Tesseract();
        tesseract.setDatapath(config.getTesseractPath());
        tesseract.setLanguage(config.getLanguage());
        logger.info("OCRProcessor initialized with language: {}", config.getLanguage());
    }

    public static OCRProcessor getInstance() {
        return INSTANCE;
    }

    public String extractTextFromImage(File imageFile) throws TesseractException {
        if (!imageFile.exists()) {
            logger.error("File not found: {}", imageFile.getAbsolutePath());
            throw new IllegalArgumentException("File not found: " + imageFile.getAbsolutePath());
        }

        try {
            logger.debug("Processing file: {}", imageFile.getName());
            String result = tesseract.doOCR(imageFile);
            logger.debug("Successfully processed file: {}", imageFile.getName());
            return result;
        } catch (TesseractException e) {
            logger.error("Failed to process file: {}", imageFile.getName(), e);
            throw e;
        }
    }
} 