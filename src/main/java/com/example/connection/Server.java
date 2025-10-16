package com.example.connection;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static Set<InetSocketAddress> clients = new HashSet<>();
    private static Map<InetSocketAddress, String> names = new HashMap<>();

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(1234)) {
            System.out.println("UDP Server listening on port 1234");

            byte[] buffer = new byte[65535];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
                String senderName = names.get(sender);

                if (message.startsWith("login:")) {
                    String name = message.substring(6).trim();
                    clients.add(sender);
                    names.put(sender, name);
                    System.out.println("Client logged in: " + name + " from " + sender);

                    // Broadcast joined
                    String broadcastMessage = "joined:" + name;
                    for (InetSocketAddress client : new HashSet<>(clients)) {
                        DatagramPacket broadcastPacket = new DatagramPacket(
                                broadcastMessage.getBytes(),
                                broadcastMessage.length(),
                                client.getAddress(),
                                client.getPort()
                        );
                        synchronized (socket) {
                            try {
                                socket.send(broadcastPacket);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    // Send list to new client
                    String listMessage = "list:" + String.join(",", names.values());
                    DatagramPacket listPacket = new DatagramPacket(
                            listMessage.getBytes(),
                            listMessage.length(),
                            sender.getAddress(),
                            sender.getPort()
                    );
                    synchronized (socket) {
                        try {
                            socket.send(listPacket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                } else if (message.equals("logout")) {
                    String name = names.get(sender);
                    if (name != null) {
                        clients.remove(sender);
                        names.remove(sender);
                        System.out.println("Client logged out: " + name);

                        // Broadcast left
                        String broadcastMessage = "left:" + name;
                        for (InetSocketAddress client : new HashSet<>(clients)) {
                            DatagramPacket broadcastPacket = new DatagramPacket(
                                    broadcastMessage.getBytes(),
                                    broadcastMessage.length(),
                                    client.getAddress(),
                                    client.getPort()
                            );
                            synchronized (socket) {
                                try {
                                    socket.send(broadcastPacket);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                } else if (message.startsWith("private:")) {
                    String[] parts = message.split(":", 3);
                    if (parts.length == 3) {
                        String targetName = parts[1];
                        String actualMessage = parts[2];
                        InetSocketAddress targetAddr = null;

                        for (Map.Entry<InetSocketAddress, String> entry : names.entrySet()) {
                            if (entry.getValue().equals(targetName)) {
                                targetAddr = entry.getKey();
                                break;
                            }
                        }

                        if (targetAddr != null) {
                            System.out.println("Private message from " + senderName + " to " + targetName + ": " + actualMessage);

                            // Send to target
                            String toTarget = "Private from " + senderName + ": " + actualMessage;
                            DatagramPacket packetToTarget = new DatagramPacket(
                                    toTarget.getBytes(),
                                    toTarget.length(),
                                    targetAddr.getAddress(),
                                    targetAddr.getPort()
                            );
                            synchronized (socket) {
                                try {
                                    socket.send(packetToTarget);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            // Send confirmation to sender
                            String toSender = "To " + targetName + ": " + actualMessage;
                            DatagramPacket packetToSender = new DatagramPacket(
                                    toSender.getBytes(),
                                    toSender.length(),
                                    sender.getAddress(),
                                    sender.getPort()
                            );
                            synchronized (socket) {
                                try {
                                    socket.send(packetToSender);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                } else if (message.startsWith("image:")) {
                    String base64 = message.substring(6);
                    String broadcastMessage = "image:" + senderName + ":" + base64;
                    for (InetSocketAddress client : new HashSet<>(clients)) {
                        DatagramPacket broadcastPacket = new DatagramPacket(broadcastMessage.getBytes(), broadcastMessage.length(), client.getAddress(), client.getPort());
                        synchronized (socket) {
                            try {
                                socket.send(broadcastPacket);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    String name = names.get(sender);
                    if (name != null) {
                        System.out.println("Message from " + name + ": " + message);

                        String broadcastMessage = name + ": " + message;
                        for (InetSocketAddress client : new HashSet<>(clients)) {
                            DatagramPacket broadcastPacket = new DatagramPacket(
                                    broadcastMessage.getBytes(),
                                    broadcastMessage.length(),
                                    client.getAddress(),
                                    client.getPort()
                            );
                            synchronized (socket) {
                                try {
                                    socket.send(broadcastPacket);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
