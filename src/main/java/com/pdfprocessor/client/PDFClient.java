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
    private int completedSearches;
    private final Object completionLock = new Object();

    public PDFClient() {
        super("PDF Search Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        executorService = Executors.newFixedThreadPool(2);
        selectedFiles = new ArrayList<>();

        JPanel topPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));

        hostField = new JTextField("localhost");
        portField = new JTextField(String.valueOf(PDFProtocol.DEFAULT_PORT));
        searchField = new JTextField();
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        selectFileButton = new JButton("Select PDF Files");
        searchButton = new JButton("Search All");
        searchButton.setEnabled(false);

        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        JScrollPane fileListScroller = new JScrollPane(fileList);
        fileListScroller.setBorder(BorderFactory.createTitledBorder("Selected Files"));

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

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        selectFileButton.addActionListener(e -> selectFiles());
        searchButton.addActionListener(e -> performSearch());

        setSize(800, 600);
        setLocationRelativeTo(null);
        
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownExecutor));
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
        int port = Integer.parseInt(portField.getText().trim());
        String searchText = searchField.getText().trim();

        if (searchText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter search text.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setUIEnabled(false);
        resultArea.setText("Processing files...\n");
        
        completedSearches = 0;

        int totalFiles = selectedFiles.size();
        for (File file : selectedFiles) {
            executorService.submit(() -> {
                processFile(file, host, port, searchText);
                synchronized (completionLock) {
                    completedSearches++;
                    if (completedSearches == totalFiles) {
                        SwingUtilities.invokeLater(() -> {
                            selectedFiles.clear();
                            fileListModel.clear();
                            setUIEnabled(true);
                            appendToResults("\n=== All files processed ===\n");
                            appendToResults("Select new files to perform another search\n");
                        });
                    }
                }
            });
        }
    }

    private void processFile(File file, String host, int port, String searchText) {
        try {
            appendToResults("\nStarting search in: " + file.getName() + "\n");
            
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 5000);
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
                        List<String> contexts = searchResponse.getContexts();
                        appendToResults("Found " + contexts.size() + " matches in " + file.getName() + ":\n");
                        for (int i = 0; i < contexts.size(); i++) {
                            appendToResults("Match " + (i + 1) + ":\n");
                            appendToResults(contexts.get(i) + "\n");
                        }
                        appendToResults("\n");
                    } else {
                        appendToResults("No matches found in " + file.getName() + "\n");
                    }
                }
            } finally {
                socket.close();
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
