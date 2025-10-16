package com.example.connection;

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.util.Base64;
import javax.swing.text.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class Client {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String name = JOptionPane.showInputDialog(null, "Enter your name:", "Login", JOptionPane.QUESTION_MESSAGE);
            if (name == null || name.trim().isEmpty()) {
                return;
            }
            name = name.trim();

            try {
                DatagramSocket socket = new DatagramSocket();
                InetAddress serverAddress = InetAddress.getByName("localhost");
                int serverPort = 1234;

                // Send login
                byte[] loginData = ("login:" + name).getBytes();
                DatagramPacket loginPacket = new DatagramPacket(loginData, loginData.length, serverAddress, serverPort);
                socket.send(loginPacket);

                JFrame frame = new JFrame("UDP Chat Client - " + name);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(600, 400);

                JTextPane textPane = new JTextPane();
                textPane.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(textPane);

                DefaultListModel<String> model = new DefaultListModel<>();
                JList<String> list = new JList<>(model);
                JScrollPane listScrollPane = new JScrollPane(list);

                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, listScrollPane);
                splitPane.setDividerLocation(400);
                splitPane.setResizeWeight(0.75);

                JTextField textField = new JTextField();
                JButton sendButton = new JButton("Send");
                JButton sendImageButton = new JButton("Send Image");

                JPanel panel = new JPanel(new BorderLayout());
                panel.add(splitPane, BorderLayout.CENTER);

                JPanel bottomPanel = new JPanel(new FlowLayout());
                bottomPanel.add(textField);
                bottomPanel.add(sendButton);
                bottomPanel.add(sendImageButton);

                panel.add(bottomPanel, BorderLayout.SOUTH);
                frame.add(panel);
                frame.setVisible(true);

                // Start receiving thread
                Thread receiver = new Thread(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            byte[] receiveData = new byte[65535];
                            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                            socket.receive(receivePacket);
                            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                            if (response.startsWith("joined:")) {
                                String joinedName = response.substring(7);
                                SwingUtilities.invokeLater(() -> model.addElement(joinedName));
                            } else if (response.startsWith("left:")) {
                                String leftName = response.substring(5);
                                SwingUtilities.invokeLater(() -> model.removeElement(leftName));
                            } else if (response.startsWith("list:")) {
                                String[] names = response.substring(5).split(",");
                                SwingUtilities.invokeLater(() -> {
                                    model.clear();
                                    for (String n : names) {
                                        if (!n.isEmpty()) model.addElement(n);
                                    }
                                });
                            } else if (response.startsWith("image:")) {
                                String[] parts = response.split(":", 3);
                                if (parts.length == 3) {
                                    String sender = parts[1];
                                    String base64 = parts[2];
                                    byte[] bytes = Base64.getDecoder().decode(base64);
                                    try {
                                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                                        if (image != null) {
                                            ImageIcon icon = new ImageIcon(image);
                                            SwingUtilities.invokeLater(() -> {
                                                try {
                                                    textPane.getDocument().insertString(textPane.getDocument().getLength(), sender + " sent an image:\n", null);
                                                    textPane.insertIcon(icon);
                                                    textPane.getDocument().insertString(textPane.getDocument().getLength(), "\n", null);
                                                } catch (BadLocationException e) {
                                                    e.printStackTrace();
                                                }
                                            });
                                        } else {
                                            SwingUtilities.invokeLater(() -> {
                                                try {
                                                    textPane.getDocument().insertString(textPane.getDocument().getLength(), sender + " sent an invalid image.\n", null);
                                                } catch (BadLocationException e) {
                                                    e.printStackTrace();
                                                }
                                            });
                                        }
                                    } catch (IOException ioEx) {
                                        SwingUtilities.invokeLater(() -> {
                                            try {
                                                textPane.getDocument().insertString(textPane.getDocument().getLength(), sender + " sent an invalid image.\n", null);
                                            } catch (BadLocationException e) {
                                                e.printStackTrace();
                                            }
                                        });
                                    }
                                }
                            } else {
                                SwingUtilities.invokeLater(() -> {
                                    try {
                                        textPane.getDocument().insertString(textPane.getDocument().getLength(), response + "\n", null);
                                    } catch (BadLocationException e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        }
                    } catch (IOException e) {
                        // Socket closed
                    }
                });
                receiver.start();

                // Send action
                ActionListener sendAction = e -> {
                    String message = textField.getText().trim();
                    if (!message.isEmpty()) {
                        String selected = list.getSelectedValue();
                        if (selected != null) {
                            message = "private:" + selected + ":" + message;
                        }
                        try {
                            byte[] sendData = message.getBytes();
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                            socket.send(sendPacket);
                            textField.setText("");
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                };

                sendButton.addActionListener(sendAction);
                textField.addActionListener(sendAction); // Enter key sends

                // Send image action
                sendImageButton.addActionListener(e -> {
                    JFileChooser chooser = new JFileChooser();
                    if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                        try {
                            byte[] bytes = Files.readAllBytes(chooser.getSelectedFile().toPath());
                            String encoded = Base64.getEncoder().encodeToString(bytes);
                            String message = "image:" + encoded;
                            byte[] sendData = message.getBytes();
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                            socket.send(sendPacket);
                        } catch (IOException ex) {
                            if (ex instanceof SocketException && ex.getMessage().contains("larger than")) {
                                JOptionPane.showMessageDialog(frame, "Image is too large to send. Please choose a smaller image (under ~50KB).");
                            } else {
                                ex.printStackTrace();
                            }
                        }
                    }
                });

                // On close, send logout
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        try {
                            byte[] sendData = "logout".getBytes();
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                            socket.send(sendPacket);
                        } catch (IOException ex) {
                            // ignore
                        }
                        socket.close();
                        receiver.interrupt();
                    }
                });

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }
}