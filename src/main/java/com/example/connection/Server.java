package com.example.connection;

import java.io.*;  // IOException
import java.net.*; // DatagramSocket, DatagramPacket, and InetSocketAddress.
import java.util.*; // Set, Map, and HashSet
import java.util.stream.Collectors; // For stream operations

public class Server {
    private static Set<InetSocketAddress> clients = new HashSet<>();
    private static Map<InetSocketAddress, String> names = new HashMap<>();
    private static Map<String, String> userStatuses = new HashMap<>();
    private static final String[] VALID_STATUSES = {"online", "invisible", "away", "busy"};
    private static String FILE_PATH = "history.txt";

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(1234)) {
            System.out.println("UDP Server listening on port 1234");

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
                    case "status":
                        handleStatus(socket, sender, message);
                        break;
                    case "private":
                        handlePrivate(socket, sender, message);
                        break;
                    case "image":
                        handleImage(socket, sender, message);
                        break;
                    case "file":
                        handleFile(socket, sender, message);
                        break;
                    case "voice":
                        handleVoice(socket, sender, message);
                        break;
                    case "typing":
                        handleTyping(socket, sender, message);
                        break;
                    default:
                        handleBroadcast(socket, sender, message);
                        break;
                }
                handleSaveToFile(message, sender);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void handleSaveToFile(String message, InetSocketAddress sender){
        if (message.contains("typing")) return;
        try {
            BufferedWriter file = new BufferedWriter(new FileWriter(FILE_PATH,true));
            file.write(sender+":"+message);
            file.newLine();
            file.close();
        } catch (Exception e) {
            System.out.println("Error writing to file : " + FILE_PATH);
        }
    }

    private static void handleLogin(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException {
        String name = message.substring(message.indexOf(':') + 1).trim();
        clients.add(sender);
        names.put(sender, name);
        userStatuses.put(name, "online"); // update or add user status
        System.out.println("Client logged in: " + name + " from " + sender);

        // Broadcast updated list (online/offline for all)
        broadcastUserList(socket);

        String listMessage = "list:" + userStatuses.entrySet().stream()
            .map(e -> e.getKey() + ":" + e.getValue())
            .collect(Collectors.joining(","));
        sendTo(socket, listMessage, sender);
    }

    private static void handleLogout(DatagramSocket socket, InetSocketAddress sender) throws IOException {
        String name = names.get(sender);
        if (name != null) {
            clients.remove(sender);
            names.remove(sender);
            userStatuses.put(name, "offline"); // mark user as offline
            System.out.println("Client logged out: " + name);

            // Broadcast updated list (online/offline for all)
            broadcastUserList(socket);
        }
    }

    // 🔧 NEW small helper to send the full list
    private static void broadcastUserList(DatagramSocket socket) throws IOException {
        String listMessage = "list:" + userStatuses.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
        broadcast(socket, listMessage);
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

            String messageToTarget = "Private from " + senderName + ": " + actualMessage;
            sendTo(socket, messageToTarget, targetAddr);

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

    private static void handleFile(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException {
        String senderName = names.get(sender);
        String messageInBase64 = message.substring(message.indexOf(':') + 1);
        String broadcastMessage = "file:" + (senderName == null ? "unknown" : senderName) + ":" + messageInBase64;
        broadcast(socket, broadcastMessage);
    }

    private static void handleVoice(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException {
        String senderName = names.get(sender);
        String messageInBase64 = message.substring(message.indexOf(':') + 1);
        String broadcastMessage = "voice:" + (senderName == null ? "unknown" : senderName) + ":" + messageInBase64;
        broadcast(socket, broadcastMessage);
    }

    private static void handleStatus(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException {
        String senderName = names.get(sender);
        if (senderName != null) {
            String newStatus = message.substring(message.indexOf(':') + 1).trim();
            // Validate status
            boolean isValidStatus = false;
            for (String validStatus : VALID_STATUSES) {
                if (validStatus.equals(newStatus)) {
                    isValidStatus = true;
                    break;
                }
            }
            if (isValidStatus) {
                userStatuses.put(senderName, newStatus);
                System.out.println("User " + senderName + " changed status to: " + newStatus);
                // Broadcast status update to all clients
                String statusMessage = "status:" + senderName + ":" + newStatus;
                broadcast(socket, statusMessage);
            }
        }
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

    // Utils
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
