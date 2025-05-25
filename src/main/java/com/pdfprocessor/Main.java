/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.pdfprocessor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author antonio
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Configure the application
        Config config = Config.getInstance();
        // You can customize these settings as needed
        // config.setMaxConcurrentThreads(4);
        // config.setLanguage("eng+fra"); // Support multiple languages

        String searchText = "forecasting";
        logger.info("Starting PDF search for text: {}", searchText);

        PDFThread pdfThread = new PDFThread(searchText);
        pdfThread.start();

        try {
            pdfThread.join(); // Wait for the search to complete
            
            List<SearchResult> results = pdfThread.getResults();
            if (results.isEmpty()) {
                logger.info("No matches found for '{}'", searchText);
            } else {
                logger.info("Found {} matches for '{}':", results.size(), searchText);
                for (SearchResult result : results) {
                    System.out.println(result);
                }
            }
        } catch (InterruptedException e) {
            logger.error("Search was interrupted: ", e);
            Thread.currentThread().interrupt();
        }
    }
}
