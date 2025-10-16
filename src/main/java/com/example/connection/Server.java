package com.example.connection;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static Set<InetSocketAddress> clients = new HashSet<>();

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(1234)) {
            System.out.println("UDP Server listening on port 1234");

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());

                if (message.equalsIgnoreCase("logout")) {
                    clients.remove(sender);
                    System.out.println("Client logged out: " + sender);
                } else {
                    clients.add(sender);
                    System.out.println("Received: " + message + " from " + sender);

                    String broadcastMessage = "From " + sender + ": " + message;
                    for (InetSocketAddress client : new HashSet<>(clients)) {  // copy to avoid concurrent mod
                        DatagramPacket broadcastPacket = new DatagramPacket(broadcastMessage.getBytes(), broadcastMessage.length(), client.getAddress(), client.getPort());
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
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}