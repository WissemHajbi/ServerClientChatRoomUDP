package com.example.connection;

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Client {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                InetAddress serverAddress = InetAddress.getByName("localhost");
                int serverPort = 1234;

                JFrame frame = new JFrame("UDP Chat Client");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(400, 300);

                JTextArea textArea = new JTextArea();
                textArea.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(textArea);

                JTextField textField = new JTextField();
                JButton sendButton = new JButton("Send");

                JPanel panel = new JPanel(new BorderLayout());
                panel.add(scrollPane, BorderLayout.CENTER);

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
                            SwingUtilities.invokeLater(() -> textArea.append(response + "\n"));
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