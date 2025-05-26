package com.pdfprocessor.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.pdfprocessor.shared.PDFProtocol;

public class PDFClient extends JFrame {
    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField searchField;
    private final JTextArea resultArea;
    private final JButton selectFileButton;
    private final JButton searchButton;
    private File selectedFile;

    public PDFClient() {
        super("PDF Search Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

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
        selectFileButton = new JButton("Select PDF File");
        searchButton = new JButton("Search");
        searchButton.setEnabled(false);

        // Add components to panels
        topPanel.add(new JLabel("Host:"));
        topPanel.add(hostField);
        topPanel.add(new JLabel("Port:"));
        topPanel.add(portField);
        topPanel.add(new JLabel("Search Text:"));
        topPanel.add(searchField);

        centerPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        bottomPanel.add(selectFileButton);
        bottomPanel.add(searchButton);

        // Add panels to frame
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Add button listeners
        selectFileButton.addActionListener(e -> selectFile());
        searchButton.addActionListener(e -> performSearch());

        // Set frame properties
        setSize(600, 400);
        setLocationRelativeTo(null);
        
        // Add border padding
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void selectFile() {
        JFileChooser fileChooser = new JFileChooser();
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
            selectedFile = fileChooser.getSelectedFile();
            selectFileButton.setText("Selected: " + selectedFile.getName());
            searchButton.setEnabled(true);
        }
    }

    private void performSearch() {
        if (selectedFile == null || !selectedFile.exists()) {
            JOptionPane.showMessageDialog(this, "Please select a PDF file first.", "Error", JOptionPane.ERROR_MESSAGE);
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
        resultArea.setText("Searching...");

        new SwingWorker<PDFProtocol.SearchResponse, Void>() {
            @Override
            protected PDFProtocol.SearchResponse doInBackground() throws Exception {
                System.out.println("Connecting to server at " + host + ":" + port);
                
                // Create socket with connection timeout
                Socket socket = null;
                try {
                    socket = new Socket();
                    socket.connect(new java.net.InetSocketAddress(host, port), 5000); // 5 seconds connection timeout
                    socket.setSoTimeout(60000); // 60 seconds read timeout
                    socket.setTcpNoDelay(true);
                    socket.setKeepAlive(true);
                    
                    System.out.println("Connected to server, creating streams...");
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush(); // Important: flush the header
                    
                    System.out.println("Output stream created, creating input stream...");
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    System.out.println("Input stream created successfully");

                    System.out.println("Reading PDF file: " + selectedFile.getName());
                    byte[] pdfContent = Files.readAllBytes(selectedFile.toPath());
                    System.out.println("PDF file size: " + pdfContent.length + " bytes");
                    
                    if (pdfContent.length > PDFProtocol.MAX_FILE_SIZE) {
                        throw new Exception("PDF file is too large. Maximum size is " + 
                            (PDFProtocol.MAX_FILE_SIZE / (1024 * 1024)) + "MB");
                    }
                    
                    System.out.println("Creating search request for phrase: " + searchText);
                    PDFProtocol.SearchRequest request = new PDFProtocol.SearchRequest(
                        pdfContent, searchText, selectedFile.getName()
                    );

                    System.out.println("Sending request to server...");
                    out.writeObject(request);
                    out.flush();
                    System.out.println("Request sent, waiting for response...");
                    
                    try {
                        Object response = in.readObject();
                        System.out.println("Response received from server");
                        
                        if (response == null) {
                            throw new Exception("Server returned null response");
                        }
                        if (!(response instanceof PDFProtocol.SearchResponse)) {
                            throw new Exception("Invalid response type from server: " + response.getClass().getName());
                        }
                        return (PDFProtocol.SearchResponse) response;
                    } catch (java.io.EOFException e) {
                        throw new Exception("Lost connection to server while waiting for response. Please try again.", e);
                    }
                } catch (Exception e) {
                    System.out.println("Error in search operation: " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                } finally {
                    if (socket != null) {
                        try {
                            if (!socket.isClosed()) {
                                socket.close();
                            }
                        } catch (Exception e) {
                            System.out.println("Error closing socket: " + e.getMessage());
                        }
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    PDFProtocol.SearchResponse response = get();
                    if (response.getError() != null) {
                        resultArea.setText("Error: " + response.getError());
                    } else if (response.isFound()) {
                        resultArea.setText("Found match!\nContext: " + response.getContext());
                    } else {
                        resultArea.setText("No matches found in the document.");
                    }
                } catch (Exception e) {
                    System.out.println("Error during search: " + e.getMessage());
                    resultArea.setText("Error: " + e.getMessage());
                } finally {
                    setUIEnabled(true);
                }
            }
        }.execute();
    }

    private void setUIEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        portField.setEnabled(enabled);
        searchField.setEnabled(enabled);
        selectFileButton.setEnabled(enabled);
        searchButton.setEnabled(enabled && selectedFile != null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PDFClient().setVisible(true);
        });
    }
} 