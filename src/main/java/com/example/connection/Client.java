package com.example.connection;

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

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

                JTextArea textArea = new JTextArea();
                textArea.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(textArea);

                DefaultListModel<String> model = new DefaultListModel<>();
                JList<String> list = new JList<>(model);
                JScrollPane listScrollPane = new JScrollPane(list);

                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, listScrollPane);
                splitPane.setDividerLocation(400);
                splitPane.setResizeWeight(0.75);

                JTextField textField = new JTextField();
                JButton sendButton = new JButton("Send");

                JPanel panel = new JPanel(new BorderLayout());
                panel.add(splitPane, BorderLayout.CENTER);

                JPanel bottomPanel = new JPanel(new BorderLayout());
                bottomPanel.add(textField, BorderLayout.CENTER);
                bottomPanel.add(sendButton, BorderLayout.EAST);

                panel.add(bottomPanel, BorderLayout.SOUTH);
                frame.add(panel);
                frame.setVisible(true);

                // Start receiving thread
                Thread receiver = new Thread(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            byte[] receiveData = new byte[1024];
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
                            } else {
                                SwingUtilities.invokeLater(() -> textArea.append(response + "\n"));
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