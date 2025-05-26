package com.pdfprocessor.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.pdfprocessor.shared.PDFProtocol;

public class PDFClient extends JFrame {
    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField searchField;
    private final JTextArea resultArea;
    private final JButton selectFileButton;
    private final JButton searchButton;
    private final DefaultListModel<String> fileListModel;
    private final JList<String> fileList;
    private final List<File> selectedFiles;
    private final ExecutorService executorService;
    private static final int MAX_CONCURRENT_SEARCHES = 2;
    private final Semaphore ocrSemaphore;

    public PDFClient() {
        super("PDF Search Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // Initialize executor service and semaphore
        executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_SEARCHES);
        ocrSemaphore = new Semaphore(1); // Only allow one OCR operation at a time
        selectedFiles = new ArrayList<>();

        // Create panels
        JPanel topPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));

        // Initialize components
        hostField = new JTextField("192.168.1.7");
        portField = new JTextField(String.valueOf(PDFProtocol.DEFAULT_PORT));
        searchField = new JTextField();
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        selectFileButton = new JButton("Select PDF Files");
        searchButton = new JButton("Search All");
        searchButton.setEnabled(false);

        // Initialize file list
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        JScrollPane fileListScroller = new JScrollPane(fileList);
        fileListScroller.setBorder(BorderFactory.createTitledBorder("Selected Files"));

        // Add components to panels
        topPanel.add(new JLabel("Host:"));
        topPanel.add(hostField);
        topPanel.add(new JLabel("Port:"));
        topPanel.add(portField);
        topPanel.add(new JLabel("Search Text:"));
        topPanel.add(searchField);

        centerPanel.add(fileListScroller, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        bottomPanel.add(selectFileButton);
        bottomPanel.add(searchButton);

        // Add panels to frame
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Add button listeners
        selectFileButton.addActionListener(e -> selectFiles());
        searchButton.addActionListener(e -> performSearch());

        // Set frame properties
        setSize(800, 600);
        setLocationRelativeTo(null);
        
        // Add border padding
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Add shutdown hook for executor
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownExecutor();
        }));
    }

    private void selectFiles() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".pdf");
            }
            public String getDescription() {
                return "PDF Files (*.pdf)";
            }
        });

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] files = fileChooser.getSelectedFiles();
            selectedFiles.clear();
            fileListModel.clear();
            for (File file : files) {
                selectedFiles.add(file);
                fileListModel.addElement(file.getName());
            }
            searchButton.setEnabled(!selectedFiles.isEmpty());
        }
    }

    private void performSearch() {
        if (selectedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select PDF files first.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String searchText = searchField.getText().trim();
        if (searchText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter search text.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Disable UI during search
        setUIEnabled(false);
        resultArea.setText("Processing files...\n");

        // Process each file concurrently
        for (File file : selectedFiles) {
            executorService.submit(() -> processFile(file, host, port, searchText));
        }
    }

    private void processFile(File file, String host, int port, String searchText) {
        try {
            appendToResults("Starting search in: " + file.getName() + "\n");
            
            // Acquire semaphore before starting OCR processing
            appendToResults("Waiting for OCR processor availability for: " + file.getName() + "\n");
            ocrSemaphore.acquire();
            appendToResults("Processing OCR for: " + file.getName() + "\n");
            
            try {
                Socket socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(60000);
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);

                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    byte[] pdfContent = Files.readAllBytes(file.toPath());
                    
                    if (pdfContent.length > PDFProtocol.MAX_FILE_SIZE) {
                        appendToResults("Error for " + file.getName() + ": File too large\n");
                        return;
                    }

                    PDFProtocol.SearchRequest request = new PDFProtocol.SearchRequest(
                        pdfContent, searchText, file.getName()
                    );

                    out.writeObject(request);
                    out.flush();

                    Object response = in.readObject();
                    if (response instanceof PDFProtocol.SearchResponse) {
                        PDFProtocol.SearchResponse searchResponse = (PDFProtocol.SearchResponse) response;
                        if (searchResponse.getError() != null) {
                            appendToResults("Error in " + file.getName() + ": " + searchResponse.getError() + "\n");
                        } else if (searchResponse.isFound()) {
                            appendToResults("Found match in " + file.getName() + "!\nContext: " + searchResponse.getContext() + "\n\n");
                        } else {
                            appendToResults("No matches found in " + file.getName() + "\n");
                        }
                    }
                } finally {
                    socket.close();
                }
            } finally {
                // Always release the semaphore in the finally block
                ocrSemaphore.release();
                appendToResults("Completed processing: " + file.getName() + "\n");
            }
        } catch (Exception e) {
            appendToResults("Error processing " + file.getName() + ": " + e.getMessage() + "\n");
        }
    }

    private void appendToResults(String text) {
        SwingUtilities.invokeLater(() -> {
            resultArea.append(text);
            resultArea.setCaretPosition(resultArea.getDocument().getLength());
        });
    }

    private void setUIEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        portField.setEnabled(enabled);
        searchField.setEnabled(enabled);
        selectFileButton.setEnabled(enabled);
        searchButton.setEnabled(enabled && !selectedFiles.isEmpty());
        if (enabled) {
            appendToResults("\n--- Search completed ---\n");
        }
    }

    private void shutdownExecutor() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PDFClient().setVisible(true);
        });
    }
} 