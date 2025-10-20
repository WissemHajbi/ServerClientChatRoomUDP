package com.example.connection;

// add small comment for every import to specify what i am importing from this exaclyt in my code 
import java.io.*; // For input/output operations like ByteArrayInputStream
import java.net.*; // For networking classes like DatagramSocket and DatagramPacket
import javax.swing.*; // For GUI components like JFrame, JTextPane, JButton
import java.awt.*; // For layout managers like BorderLayout and event classes
import java.awt.event.*; // For event handling like ActionListener and WindowAdapter
import java.nio.file.Files; // For reading file bytes
import java.util.Base64; // For encoding/decoding binary data to Base64 strings
import javax.swing.text.*; // For text components like JTextPane and document manipulation
import java.awt.image.BufferedImage; // For handling image data
import javax.imageio.ImageIO; // For reading images from byte streams

public class Client {

    public static void main(String[] args) {
        // SwingUtilities.invokeLater ensures GUI updates happen on the Event Dispatch Thread (EDT).
        // This is required for Swing components to work properly and avoid threading issues.
        // We can't simplify it further because Swing is not thread-safe; all GUI operations must be on EDT.
        SwingUtilities.invokeLater(() -> {

            // Get the user's name using the extracted method
            String name = getUserName();
            if (name == null) {
                return; // Exit if no name provided
            }

            try {
                DatagramSocket socket = new DatagramSocket(); // auto assigned port
                InetAddress serverAddress = InetAddress.getByName("localhost"); // point de communication server
                int serverPort = 1234;

                // Send login
                byte[] loginData = ("login:" + name).getBytes();
                DatagramPacket loginPacket = new DatagramPacket(loginData, loginData.length, serverAddress, serverPort);
                socket.send(loginPacket);

                // Create GUI using the extracted method
                GUIComponents comps = createGUI(name);

                // Start receiving thread
                // A thread is like a separate worker that runs code in parallel to the main program.
                // We need a thread for receiving because UDP socket.receive() blocks (waits) until a message arrives.
                // If we did this in the main thread, the GUI would freeze and not respond to user clicks.
                // So, we run receiving in a background thread to keep the GUI responsive.
                Thread receiver = new Thread(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            byte[] receiveData = new byte[65535];
                            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                            socket.receive(receivePacket);
                            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                            // Follow server structure: use switch with handlers
                            handleReceive(response, comps);
                        }
                    } catch (IOException e) {
                        // Socket closed
                    }
                });
                receiver.start();

                // Send action
                // ActionListener is an interface that lets us respond to events like button clicks.
                // We use it explicitly because GUI events are event-driven; the system calls our code when the event happens,
                // rather than us constantly checking (polling), which would waste CPU and make the app unresponsive.
                ActionListener sendAction = e -> sendMessage(comps, socket, serverAddress, serverPort);
                comps.sendButton.addActionListener(sendAction);
                comps.textField.addActionListener(sendAction); // Enter key sends

                // Send image action
                comps.sendImageButton.addActionListener(e -> sendImage(comps, socket, serverAddress, serverPort));

                // Logout action
                comps.logoutButton.addActionListener(e -> logout(socket, receiver, serverAddress, serverPort, comps.frame));

                // On close, handle logout
                handleWindowClose(comps.frame, socket, receiver, serverAddress, serverPort);
             
                
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    private static String getUserName() {
        String name = JOptionPane.showInputDialog(null, "Enter your name:", "Login", JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            return null; 
        }
        return name.trim();
    }

    private static class GUIComponents {
        JFrame frame;
        JTextPane textPane;
        DefaultListModel<String> model;
        JList<String> list;
        JTextField textField;
        JButton sendButton;
        JButton sendImageButton;
        JButton logoutButton;
    }

    private static GUIComponents createGUI(String name) {
        GUIComponents comps = new GUIComponents();

        comps.frame = new JFrame("UDP Chat Client - " + name);
        comps.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        comps.frame.setSize(600, 400);

        comps.textPane = new JTextPane();
        comps.textPane.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(comps.textPane);

        comps.model = new DefaultListModel<>();
        comps.list = new JList<>(comps.model);
        JScrollPane listScrollPane = new JScrollPane(comps.list);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, listScrollPane);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.75);

        comps.textField = new JTextField();
        comps.textField.setPreferredSize(new Dimension(300, 25)); // Fix width issue
        comps.sendButton = new JButton("Send");
        comps.sendImageButton = new JButton("Send Image");
        comps.logoutButton = new JButton("Logout");

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout());
        bottomPanel.add(comps.textField);
        bottomPanel.add(comps.sendButton);
        bottomPanel.add(comps.sendImageButton);
        bottomPanel.add(comps.logoutButton);

        panel.add(bottomPanel, BorderLayout.SOUTH);
        comps.frame.add(panel);
        comps.frame.setVisible(true);

        return comps;
    }

    // handlers
    private static void handleJoined(String response, GUIComponents comps) {
        String joinedName = response.substring(7);
        SwingUtilities.invokeLater(() -> comps.model.addElement(joinedName));
    }

    private static void handleLeft(String response, GUIComponents comps) {
        String leftName = response.substring(5);
        SwingUtilities.invokeLater(() -> comps.model.removeElement(leftName));
    }

    private static void handleList(String response, GUIComponents comps) {
        String[] names = response.substring(5).split(",");
        SwingUtilities.invokeLater(() -> {
            comps.model.clear();
            for (String n : names) {
                if (!n.isEmpty()) comps.model.addElement(n);
            }
        });
    }

    // Image sending/receiving/displaying: 
    // Sending: Read image file as bytes, encode to Base64 string, send as "image:base64string"
    // Receiving: Decode Base64 back to bytes, use ImageIO.read to create BufferedImage from ByteArrayInputStream
    // Displaying: Create ImageIcon from BufferedImage, insert into JTextPane document like text
    private static void handleImageReceive(String response, GUIComponents comps) {
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
                            comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), sender + " sent an image:\n", null);
                            comps.textPane.insertIcon(icon);
                            comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), "\n", null);
                        } catch (BadLocationException e) {
                            e.printStackTrace();
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), sender + " sent an invalid image.\n", null);
                        } catch (BadLocationException e) {
                            e.printStackTrace();
                        }
                    });
                }
            } catch (IOException ioEx) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), sender + " sent an invalid image.\n", null);
                    } catch (BadLocationException e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    private static void handleMessage(String response, GUIComponents comps) {
        SwingUtilities.invokeLater(() -> {
            try {
                comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), response + "\n", null);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private static void handleReceive(String response, GUIComponents comps) {
        if (response.startsWith("joined:")) {
            handleJoined(response, comps);
        } else if (response.startsWith("left:")) {
            handleLeft(response, comps);
        } else if (response.startsWith("list:")) {
            handleList(response, comps);
        } else if (response.startsWith("image:")) {
            handleImageReceive(response, comps);
        } else {
            handleMessage(response, comps);
        }
    }

    private static void sendMessage(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        String message = comps.textField.getText().trim();
        if (!message.isEmpty()) {
            String selected = comps.list.getSelectedValue();
            if (selected != null) {
                message = "private:" + selected + ":" + message;
            }
            try {
                byte[] sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                socket.send(sendPacket);
                comps.textField.setText("");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void sendImage(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(comps.frame) == JFileChooser.APPROVE_OPTION) {
            try {
                byte[] bytes = Files.readAllBytes(chooser.getSelectedFile().toPath());
                String encoded = Base64.getEncoder().encodeToString(bytes);
                String message = "image:" + encoded;
                byte[] sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                socket.send(sendPacket);
            } catch (IOException ex) {
                if (ex instanceof SocketException && ex.getMessage().contains("larger than")) {
                    JOptionPane.showMessageDialog(comps.frame, "Image is too large to send. Please choose a smaller image (under ~50KB).");
                } else {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static void handleWindowClose(JFrame frame, DatagramSocket socket, Thread receiver, InetAddress serverAddress, int serverPort) {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    byte[] sendData = "logout".getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                    socket.send(sendPacket);
                } catch (IOException ex) {
                    System.out.println("Error logging out");
                }
                socket.close();
                receiver.interrupt();
            }
        });
    }

    private static void logout(DatagramSocket socket, Thread receiver, InetAddress serverAddress, int serverPort, JFrame frame) {
        try {
            byte[] sendData = "logout".getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            socket.send(sendPacket);
        } catch (IOException ex) {
            // ignore
        }
        socket.close();
        receiver.interrupt();
        frame.dispose();
    }

    
}