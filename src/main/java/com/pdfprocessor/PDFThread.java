/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.pdfprocessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.tess4j.TesseractException;

/**
 *
 * @author antonio
 */
public class PDFThread extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(PDFThread.class);
    private final String searchText;
    private final List<SearchResult> results;
    private static final Semaphore semaphore;
    
    static {
        Config config = Config.getInstance();
        semaphore = new Semaphore(config.getMaxConcurrentThreads());
    }

    public PDFThread(String searchText) {
        this.searchText = searchText.toLowerCase();
        this.results = new ArrayList<>();
    }

    public List<SearchResult> getResults() {
        return results;
    }

    @Override
    public void run() {
        logger.info("Starting search for text: {}", searchText);

        try {
            semaphore.acquire();
            logger.debug("Acquired semaphore for processing");

            File[] files = new File(Config.getInstance().getFilesDirectory()).listFiles();
            if (files == null) {
                logger.error("Failed to list files in directory: {}", Config.getInstance().getFilesDirectory());
                return;
            }

            OCRProcessor processor = OCRProcessor.getInstance();

            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".pdf")) {
                    continue;
                }

                try {
                    logger.debug("Processing file: {}", file.getName());
                    String result = processor.extractTextFromImage(file);
                    String formattedResult = result.replaceAll("\\r|\\n", " ").toLowerCase();
                    
                    if (formattedResult.contains(searchText)) {
                        int index = formattedResult.indexOf(searchText);
                        String context = extractContext(formattedResult, index);
                        results.add(new SearchResult(file.getName(), context));
                        logger.info("Found match in file: {}", file.getName());
                    }
                } catch (TesseractException e) {
                    logger.error("Error processing file {}: {}", file.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error during processing: ", e);
        } finally {
            semaphore.release();
            logger.debug("Released semaphore");
        }

        logger.info("Search completed. Found {} matches", results.size());
    }

    private String extractContext(String text, int matchIndex) {
        int contextSize = 50;
        int start = Math.max(0, matchIndex - contextSize);
        int end = Math.min(text.length(), matchIndex + searchText.length() + contextSize);
        return "..." + text.substring(start, end) + "...";
    }
}
