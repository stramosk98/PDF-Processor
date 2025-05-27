package com.pdfprocessor.shared;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PDFProtocol {
    public static final int DEFAULT_PORT = 12349;
    public static final int MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    public static class SearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private final byte[] pdfContent;
        private final String searchText;
        private final String fileName;

        public SearchRequest(byte[] pdfContent, String searchText, String fileName) {
            this.pdfContent = pdfContent;
            this.searchText = searchText;
            this.fileName = fileName;
        }

        public byte[] getPdfContent() {
            return pdfContent;
        }

        public String getSearchText() {
            return searchText;
        }

        public String getFileName() {
            return fileName;
        }
    }

    public static class SearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private final boolean found;
        private final List<String> contexts;
        private final String error;

        public SearchResponse(boolean found, List<String> contexts, String error) {
            this.found = found;
            this.contexts = contexts != null ? contexts : new ArrayList<>();
            this.error = error;
        }

        public boolean isFound() {
            return found;
        }

        public List<String> getContexts() {
            return contexts;
        }

        public String getError() {
            return error;
        }
    }
} 