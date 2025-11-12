package com.example.connection; // Package for the connection classes

import java.io.*;  // IOException for handling IO exceptions
import java.net.*; // DatagramSocket, DatagramPacket, and InetSocketAddress for UDP networking
import java.util.*; // Set, Map, and HashSet for collections
import java.util.stream.Collectors; // For stream operations on collections

public class Server { // Main server class for UDP chat server
    private static Map<InetSocketAddress, String> names = new HashMap<>(); // Map client addresses to usernames
    
    private static Set<InetSocketAddress> clients = new HashSet<>(); // Set of connected client addresses
    private static Map<String, String> userStatuses = new HashMap<>(); // Map usernames to their statuses

    private static final String[] VALID_STATUSES = {"online", "invisible", "away", "busy"}; // Valid status values
    private static String FILE_PATH = "history.txt"; // Path to history file

    public static void main(String[] args) { // Entry point for the server application
        try (DatagramSocket socket = new DatagramSocket(1234)) { // Create UDP socket on port 1234, auto-close on exit
            System.out.println("UDP Server listening on port 1234"); // Print startup message

            byte[] buffer = new byte[65535]; // Buffer for incoming packets (max UDP size)

            while (true) { // Infinite loop to handle incoming packets
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length); // Create packet for receiving
                socket.receive(packet); // Block and wait for incoming packet

                String message = new String(packet.getData(), 0, packet.getLength()); // Convert packet data to string
                InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort()); // Get sender address

                String action; // Variable to hold the action type
                int index = message.indexOf(':'); // Find colon separator
                if (index != -1) action = message.substring(0, index); else action = message; // Extract action before colon, or whole message

                switch (action) { // Switch based on action type
                    case "login": // User login
                        handleLogin(socket, sender, message); // Handle login
                        break;
                    case "logout": // User logout
                        handleLogout(socket, sender); // Handle logout
                        break;
                    case "status": // Status update
                        handleStatus(socket, sender, message); // Handle status change
                        break;
                    case "private": // Private message
                        handlePrivate(socket, sender, message); // Handle private message
                        break;
                    case "image": // Image message
                        handleImage(socket, sender, message); // Handle image
                        break;
                    case "file": // File message
                        handleFile(socket, sender, message); // Handle file
                        break;
                    case "voice": // Voice message
                        handleVoice(socket, sender, message); // Handle voice
                        break;
                    case "typing": // Typing indicator
                        handleTyping(socket, sender, message); // Handle typing
                        break;
                    default: // Regular broadcast message
                        handleBroadcast(socket, sender, message); // Handle broadcast
                        break;
                }
                handleSaveToFile(message, sender); // Save message to history file
            }
        } catch (IOException ex) { // Catch IO exceptions
            ex.printStackTrace(); // Print stack trace
        }
    }

    private static void handleSaveToFile(String message, InetSocketAddress sender){ // Save message to history file
        if (message.contains("typing")) return; // Skip typing indicators
        try { // Try to write to file
            BufferedWriter file = new BufferedWriter(new FileWriter(FILE_PATH,true)); // Open file in append mode
            file.write(sender+":"+message); // Write sender and message
            file.newLine(); // New line
            file.close(); // Close file
        } catch (Exception e) { // Catch exceptions
            System.out.println("Error writing to file : " + FILE_PATH); // Print error
        }
    }

    private static void handleLogin(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException { // Handle user login
        String name = message.substring(message.indexOf(':') + 1).trim(); // Extract username from message
        clients.add(sender); // Add client to connected set
        names.put(sender, name); // Map address to name
        userStatuses.put(name, "online"); // Set user status to online
        System.out.println("Client logged in: " + name + " from " + sender); // Log login

        // Broadcast updated list (online/offline for all)
        broadcastUserList(socket); // Broadcast user list to all clients

        String listMessage = "list:" + userStatuses.entrySet().stream() // Create list message
            .map(e -> e.getKey() + ":" + e.getValue()) // Format as name:status
            .collect(Collectors.joining(",")); // Join with commas
        sendTo(socket, listMessage, sender); // Send list to new client
    }

    private static void handleLogout(DatagramSocket socket, InetSocketAddress sender) throws IOException { // Handle user logout
        String name = names.get(sender); // Get username from address
        if (name != null) { // If user was logged in
            clients.remove(sender); // Remove from clients
            names.remove(sender); // Remove name mapping
            userStatuses.put(name, "offline"); // Set status to offline
            System.out.println("Client logged out: " + name); // Log logout

            // Broadcast updated list (online/offline for all)
            broadcastUserList(socket); // Broadcast updated list
        }
    }

    // 🔧 NEW small helper to send the full list
    private static void broadcastUserList(DatagramSocket socket) throws IOException { // Broadcast user list to all clients
        String listMessage = "list:" + userStatuses.entrySet().stream() // Create list message
                .map(e -> e.getKey() + ":" + e.getValue()) // Format each entry
                .collect(Collectors.joining(",")); // Join with commas
        broadcast(socket, listMessage); // Broadcast to all
    }

    private static void handlePrivate(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException { // Handle private message
        String[] parts = message.split(":", 3); // Split "private:target:message"
        if (parts.length != 3) return; // Invalid format
        String targetName = parts[1]; // Extract target username
        String actualMessage = parts[2]; // Extract message content

        InetSocketAddress targetAddr = findAddressByName(targetName); // Find target's address
        String senderName = names.get(sender); // Get sender's name
        if (targetAddr != null && senderName != null) { // If both exist
            System.out.println("Private message from " + senderName + " to " + targetName + ": " + actualMessage); // Log

            String messageToTarget = "Private from " + senderName + ": " + actualMessage; // Format for target
            sendTo(socket, messageToTarget, targetAddr); // Send to target

            String messageToSender = "To " + targetName + ": " + actualMessage; // Format for sender
            sendTo(socket, messageToSender, sender); // Send to sender
        }
    }

    private static void handleImage(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException { // Handle image message
        String senderName = names.get(sender); // Get sender's name
        String messageInBase64 = message.substring(message.indexOf(':') + 1); // Extract base64 data
        String broadcastMessage = "image:" + (senderName == null ? "unknown" : senderName) + ":" + messageInBase64; // Format broadcast message
        broadcastExceptSender(socket, broadcastMessage, sender); // Broadcast to all except sender
    }

    private static void handleFile(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException { // Handle file message
        String senderName = names.get(sender); // Get sender's name
        String messageInBase64 = message.substring(message.indexOf(':') + 1); // Extract base64 data
        String broadcastMessage = "file:" + (senderName == null ? "unknown" : senderName) + ":" + messageInBase64; // Format broadcast message
        broadcast(socket, broadcastMessage); // Broadcast to all
    }

    private static void handleVoice(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException { // Handle voice message
        String senderName = names.get(sender); // Get sender's name
        String messageInBase64 = message.substring(message.indexOf(':') + 1); // Extract base64 data
        String broadcastMessage = "voice:" + (senderName == null ? "unknown" : senderName) + ":" + messageInBase64; // Format broadcast message
        broadcast(socket, broadcastMessage); // Broadcast to all
    }

    private static void handleStatus(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException { // Handle status update
        String senderName = names.get(sender); // Get sender's name
        if (senderName != null) { // If user exists
            String newStatus = message.substring(message.indexOf(':') + 1).trim(); // Extract new status
            // Validate status
            boolean isValidStatus = false; // Flag for validity
            for (String validStatus : VALID_STATUSES) { // Check against valid statuses
                if (validStatus.equals(newStatus)) { // If matches
                    isValidStatus = true; // Set valid
                    break; // Exit loop
                }
            }
            if (isValidStatus) { // If valid
                userStatuses.put(senderName, newStatus); // Update status
                System.out.println("User " + senderName + " changed status to: " + newStatus); // Log
                // Broadcast status update to all clients
                String statusMessage = "status:" + senderName + ":" + newStatus; // Format message
                broadcast(socket, statusMessage); // Broadcast
            }
        }
    }

    private static void handleTyping(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException { // Handle typing indicator
        String name = names.get(sender); // Get sender's name
        if (name != null) { // If user exists
            broadcast(socket, message); // Broadcast typing message
        }
    }

    private static void handleBroadcast(DatagramSocket socket, InetSocketAddress sender, String message) throws IOException { // Handle regular broadcast message
        String name = names.get(sender); // Get sender's name
        if (name == null) return; // If not logged in, ignore
        System.out.println("Message from " + name + ": " + message); // Log message
        String broadcastMessage = name + ": " + message; // Format broadcast message
        broadcast(socket, broadcastMessage); // Broadcast to all
    }

    // Utils
    private static InetSocketAddress findAddressByName(String name) { // Find client address by username
        for (Map.Entry<InetSocketAddress, String> entry : names.entrySet()) { // Iterate through names map
            if (entry.getValue().equals(name)) return entry.getKey(); // Return address if name matches
        }
        return null; // Not found
    }

    private static void sendTo(DatagramSocket socket, String message, InetSocketAddress target) throws IOException { // Send message to specific client
        DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), target.getAddress(), target.getPort()); // Create packet
        synchronized (socket) { // Synchronize socket access
            socket.send(packet); // Send packet
        }
    }

    private static void broadcast(DatagramSocket socket, String message) throws IOException { // Broadcast message to all clients
        for (InetSocketAddress client : new HashSet<>(clients)) { // Iterate through clients
            sendTo(socket, message, client); // Send to each
        }
    }

    private static void broadcastExceptSender(DatagramSocket socket, String message, InetSocketAddress sender) throws IOException { // Broadcast except to sender
        for (InetSocketAddress client : new HashSet<>(clients)) { // Iterate through clients
            if (!client.equals(sender)) { // If not sender
                sendTo(socket, message, client); // Send to client
            }
        }
    }
}
