package com.example.connection;

import java.io.*;  // IOException
import java.net.*; // DatagramSocket, DatagramPacket, and InetSocketAddress.
import java.util.*; // Set, Map, and HashSet

public class Server {
    // InetSocketAddress = point de communication
    private static Set<InetSocketAddress> clients = new HashSet<>();
    private static Map<InetSocketAddress, String> names = new HashMap<>();

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(1234)) { // socket bound to all local addresses on port 1234
            System.out.println("UDP Server listening on port 1234");

            // UDP packets can be up to 65507 bytes of payload
            byte[] buffer = new byte[65535]; 

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());

                String action;
                int index = message.indexOf(':');
                if (index != -1) action = message.substring(0, index); else action = message;

                switch (action) {
                    case "login":
                        handleLogin(socket, sender, message);
                        break;
                    case "logout":
                        handleLogout(socket, sender);
                        break;
                    case "private":
                        handlePrivate(socket, sender, message);
                        break;
                    case "image":
                        handleImage(socket, sender, message);
                        break;
                    case "typing":
                        handleTyping(socket, sender, message);
                        break;
                    default:
                        handleBroadcast(socket, sender, message);
                        break;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // Handles
    private static void handleLogin(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException {
        String name = message.substring(message.indexOf(':') + 1).trim();
        clients.add(sender);
        names.put(sender, name);
        System.out.println("Client logged in: " + name + " from " + sender);

        // Broadcast joined
        String broadcastMessage = "joined:" + name;
        broadcast(socket, broadcastMessage);

        // Send list to new client
        String listMessage = "list:" + String.join(",", names.values());
        sendTo(socket, listMessage, sender);
    }

    private static void handleLogout(DatagramSocket socket, InetSocketAddress sender) throws IOException {
        String name = names.get(sender);
        if (name != null) {
            clients.remove(sender);
            names.remove(sender);
            System.out.println("Client logged out: " + name);

            // Broadcast left
            String broadcastMessage = "left:" + name;
            broadcast(socket, broadcastMessage);
        }
    }

    private static void handlePrivate(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException {
        String[] parts = message.split(":", 3);
        if (parts.length != 3) return;
        String targetName = parts[1];
        String actualMessage = parts[2];

        InetSocketAddress targetAddr = findAddressByName(targetName);
        String senderName = names.get(sender);
        if (targetAddr != null && senderName != null) {
            System.out.println("Private message from " + senderName + " to " + targetName + ": " + actualMessage);

            // Send to target
            String messageToTarget = "Private from " + senderName + ": " + actualMessage;
            sendTo(socket, messageToTarget, targetAddr);

            // Send confirmation to sender
            String messageToSender = "To " + targetName + ": " + actualMessage;
            sendTo(socket, messageToSender, sender);
        }
    }

    private static void handleImage(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException {
        String senderName = names.get(sender);
        String messageInBase64 = message.substring(message.indexOf(':') + 1);
        String broadcastMessage = "image:" + (senderName == null ? "unknown" : senderName) + ":" + messageInBase64;
        broadcast(socket, broadcastMessage);
    }

    private static void handleTyping(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException {
        String name = names.get(sender);
        if (name != null) {
            broadcast(socket, message);
        }
    }

    private static void handleBroadcast(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException {
        String name = names.get(sender);
        if (name == null) return;
        System.out.println("Message from " + name + ": " + message);
        String broadcastMessage = name + ": " + message;
        broadcast(socket, broadcastMessage);
    }

    // Util 
    private static InetSocketAddress findAddressByName(String name) {
        for (Map.Entry<InetSocketAddress, String> entry : names.entrySet()) {
            if (entry.getValue().equals(name)) return entry.getKey();
        }
        return null;
    }

    private static void sendTo(DatagramSocket socket, String message, InetSocketAddress target) throws IOException {
        DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), target.getAddress(), target.getPort());
        synchronized (socket) {
            socket.send(packet);
        }
    }

    private static void broadcast(DatagramSocket socket, String message) throws IOException {
        for (InetSocketAddress client : new HashSet<>(clients)) {
            sendTo(socket, message, client);
        }
    }
}
