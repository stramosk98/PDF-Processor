package com.pdfprocessor.shared;

import java.io.Serializable;

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
        private final String context;
        private final String error;

        public SearchResponse(boolean found, String context, String error) {
            this.found = found;
            this.context = context;
            this.error = error;
        }

        public boolean isFound() {
            return found;
        }

        public String getContext() {
            return context;
        }

        public String getError() {
            return error;
        }
    }
} 