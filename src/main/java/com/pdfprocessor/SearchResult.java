package com.pdfprocessor;

public class SearchResult {
    private final String fileName;
    private final String matchContext;

    public SearchResult(String fileName, String matchContext) {
        this.fileName = fileName;
        this.matchContext = matchContext;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMatchContext() {
        return matchContext;
    }

    @Override
    public String toString() {
        return String.format("Match in %s:\n%s", fileName, matchContext);
    }
} 