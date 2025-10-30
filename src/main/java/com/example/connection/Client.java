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
import java.time.LocalTime; // For timestamps
import java.time.format.DateTimeFormatter; // For formatting timestamps
import javax.sound.sampled.*; // For audio recording and playback
import java.io.ByteArrayInputStream; // For audio data streams
import java.io.File; // For file operations
import java.util.prefs.Preferences; // For saving user preferences

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

                // Create GUI using the extracted method
                GUIComponents comps = createGUI(name);

                // Add typing listener
                comps.textField.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        if (!comps.textField.getText().trim().isEmpty() && System.currentTimeMillis() - comps.lastTypingTime > 1000) {
                            sendTyping(comps, socket, serverAddress, serverPort);
                            comps.lastTypingTime = System.currentTimeMillis();
                        }
                    }
                });

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

                // Load saved status and set initial status
                String savedStatus = loadSavedStatus();
                comps.currentStatus = savedStatus;
                comps.statusButton.setText("Status: " + savedStatus.substring(0, 1).toUpperCase() + savedStatus.substring(1));

                // Send login
                byte[] loginData = ("login:" + name).getBytes();
                DatagramPacket loginPacket = new DatagramPacket(loginData, loginData.length, serverAddress, serverPort);
                socket.send(loginPacket);

                // Send initial status
                String statusMessage = "status:" + savedStatus;
                byte[] statusData = statusMessage.getBytes();
                DatagramPacket statusPacket = new DatagramPacket(statusData, statusData.length, serverAddress, serverPort);
                socket.send(statusPacket);

                // Send action
                // ActionListener is an interface that lets us respond to events like button clicks.
                // We use it explicitly because GUI events are event-driven; the system calls our code when the event happens,
                // rather than us constantly checking (polling), which would waste CPU and make the app unresponsive.
                ActionListener sendAction = e -> sendMessage(comps, socket, serverAddress, serverPort);
                comps.sendButton.addActionListener(sendAction);
                comps.textField.addActionListener(sendAction); // Enter key sends

                // Send image action
                comps.sendImageButton.addActionListener(e -> sendImage(comps, socket, serverAddress, serverPort));

                // Send file action
                comps.sendFileButton.addActionListener(e -> sendFile(comps, socket, serverAddress, serverPort));

                // Record voice action
                comps.recordVoiceButton.addActionListener(e -> recordVoice(comps, socket, serverAddress, serverPort));

                // Status change action
                comps.statusButton.addActionListener(e -> changeStatus(comps, socket, serverAddress, serverPort));

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
        DefaultListModel<User> model;
        JList<User> list;
        JTextField textField;
        JButton sendButton;
        JButton sendImageButton;
        JButton sendFileButton;
        JButton recordVoiceButton;
        JButton statusButton;
        JButton logoutButton;
        JLabel typingLabel;
        String userName;
        String currentStatus;
        Timer typingTimer;
        long lastTypingTime;
        boolean isRecording;
        TargetDataLine microphone;
        ByteArrayOutputStream audioBuffer;
        Timer recordingTimer;
    }

    private static class User {
        String name;
        String status;

        User(String name, String status) {
            this.name = name;
            this.status = status;
        }

        boolean isVisible() {
            return !"invisible".equals(status);
        }
    }

    /**
     * Icon implementation that draws an anti-aliased filled circle.
     */
    private static class CircleIcon implements Icon {
        private final Color color;
        private final int size;

        CircleIcon(Color color, int size) {
            this.color = color == null ? Color.GRAY : color;
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x, y, size, size);
                g2.setColor(color.darker());
                g2.drawOval(x, y, size - 1, size - 1);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Renders a user row with a small colored circle icon to represent status.
     * This avoids relying on emoji rendering which can look inconsistent across platforms.
     */
    private static class UserCellRenderer extends DefaultListCellRenderer {
        private final int iconSize = 12;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof User) {
                User user = (User) value;
                setText(user.name);
                setIcon(new CircleIcon(getStatusColor(user.status), iconSize));
                setIconTextGap(8);
            } else {
                setIcon(null);
            }
            return this;
        }

        private Color getStatusColor(String status) {
            switch (status) {
                case "online": return new Color(0x1DB954); // pleasant green
                case "invisible": return Color.LIGHT_GRAY;
                case "away": return new Color(0xFFC107); // amber
                case "busy": return new Color(0xD32F2F); // red
                default: return Color.GRAY;
            }
        }
    }

    private static GUIComponents createGUI(String name) {
        GUIComponents comps = new GUIComponents();
        comps.userName = name;
        comps.lastTypingTime = System.currentTimeMillis() - 2000; // Allow immediate first send

        comps.frame = new JFrame("UDP Chat Client - " + name);
        comps.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        comps.frame.setSize(800, 400);

        comps.textPane = new JTextPane();
        comps.typingLabel = new JLabel("");
        comps.typingLabel.setForeground(Color.GRAY);
        comps.textPane.setEditable(false);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.add(comps.typingLabel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(comps.textPane);
        chatPanel.add(scrollPane, BorderLayout.CENTER);
        JScrollPane chatScrollPane = new JScrollPane(chatPanel);

        comps.model = new DefaultListModel<>();
        comps.list = new JList<>(comps.model);
        comps.list.setCellRenderer(new UserCellRenderer());
        JScrollPane listScrollPane = new JScrollPane(comps.list);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScrollPane, listScrollPane);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.75);

        comps.textField = new JTextField();
        comps.textField.setPreferredSize(new Dimension(200, 25)); // Fix width issue
        comps.sendButton = new JButton("Send");
        comps.sendImageButton = new JButton("Send Image");
        comps.sendFileButton = new JButton("Send File");
        comps.recordVoiceButton = new JButton("Record Voice");
        comps.statusButton = new JButton("Status: Online");
        comps.logoutButton = new JButton("Logout");
        comps.isRecording = false;
        comps.audioBuffer = new ByteArrayOutputStream();
        comps.currentStatus = "online";

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout());
        bottomPanel.add(comps.textField);
        bottomPanel.add(comps.sendButton);
        bottomPanel.add(comps.sendImageButton);
        bottomPanel.add(comps.sendFileButton);
        bottomPanel.add(comps.recordVoiceButton);
        bottomPanel.add(comps.statusButton);
        bottomPanel.add(comps.logoutButton);

        panel.add(bottomPanel, BorderLayout.SOUTH);
        comps.frame.add(panel);
        comps.frame.setVisible(true);

        return comps;
    }

    // handlers
    private static void handleJoined(String response, GUIComponents comps) {
        String name = response.substring(7);
        SwingUtilities.invokeLater(() -> {
            User user = findUserByName(comps.model, name);
            if (user == null) {
                comps.model.addElement(new User(name, "online"));
            } else {
                user.status = "online";
                comps.list.repaint();
            }
        });
    }

    private static void handleLeft(String response, GUIComponents comps) {
        String name = response.substring(5);
        SwingUtilities.invokeLater(() -> {
            User user = findUserByName(comps.model, name);
            if (user != null) {
                user.status = "offline";
                comps.list.repaint();
            }
        });
    }

    private static void handleList(String response, GUIComponents comps) {
    String[] parts = response.substring(5).split(",");
    SwingUtilities.invokeLater(() -> {
        comps.model.clear();
        for (String part : parts) {
            if (!part.isEmpty()) {
                String[] sub = part.split(":");
                if (sub.length == 2) {
                    String name = sub[0];
                    String status = sub[1];
                    if (!name.equals(comps.userName)) { // exclude self
                        // Only show visible users (not invisible status)
                        User user = new User(name, status);
                        if (user.isVisible()) {
                            comps.model.addElement(user);
                        }
                    }
                }
            }
        }
    });
}

    private static void handleStatusUpdate(String response, GUIComponents comps) {
        String[] parts = response.split(":");
        if (parts.length == 3 && "status".equals(parts[0])) {
            String userName = parts[1];
            String newStatus = parts[2];
            SwingUtilities.invokeLater(() -> {
                User user = findUserByName(comps.model, userName);
                if (user != null) {
                    user.status = newStatus;
                    if (!user.isVisible()) {
                        comps.model.removeElement(user);
                    }
                    comps.list.repaint();
                } else if (!"invisible".equals(newStatus) && !"offline".equals(newStatus)) {
                    // Add user if they become visible
                    comps.model.addElement(new User(userName, newStatus));
                }
            });
        }
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
                            comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), timeStamp() + sender + " sent an image:\n", null);
                            comps.textPane.insertIcon(icon);
                            comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), "\n", null);
                        } catch (BadLocationException e) {
                            e.printStackTrace();
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), timeStamp() + sender + " sent an invalid image.\n", null);
                        } catch (BadLocationException e) {
                            e.printStackTrace();
                        }
                    });
                }
            } catch (IOException ioEx) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), timeStamp() + sender + " sent an invalid image.\n", null);
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
                comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), timeStamp() + response + "\n", null);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    // Return a short timestamp like [14:32] to prefix messages
    private static String timeStamp() {
        return "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "] ";
    }

    private static void handleReceive(String response, GUIComponents comps) {
        if (response.startsWith("joined:")) {
            handleJoined(response, comps);
        } else if (response.startsWith("left:")) {
            handleLeft(response, comps);
        } else if (response.startsWith("list:")) {
            handleList(response, comps);
        } else if (response.startsWith("status:")) {
            handleStatusUpdate(response, comps);
        } else if (response.startsWith("image:")) {
            handleImageReceive(response, comps);
        } else if (response.startsWith("file:")) {
            handleFileReceive(response, comps);
        } else if (response.startsWith("voice:")) {
            handleVoiceReceive(response, comps);
        } else if (response.startsWith("typing:")){
            handleTyping(response, comps);
        } else{
            handleMessage(response, comps);
        }
    }

    private static void sendMessage(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        String message = comps.textField.getText().trim();
        if (!message.isEmpty()) {
            User selected = comps.list.getSelectedValue();
            if (selected != null) {
                message = "private:" + selected.name + ":" + message;
            }
            try {
                byte[] sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                socket.send(sendPacket);
                comps.textField.setText("");
                comps.typingLabel.setText(""); // Clear typing indicator
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void sendTyping(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        try {
            String message = "typing:" + comps.userName;
            byte[] sendData = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            socket.send(sendPacket);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void sendImage(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(comps.frame) == JFileChooser.APPROVE_OPTION) {
            try {
                byte[] bytes = Files.readAllBytes(chooser.getSelectedFile().toPath());

                // Check file size (UDP limit is ~65KB, leave buffer)
                if (bytes.length > 60000) {
                    JOptionPane.showMessageDialog(comps.frame, "Image is too large to send. Maximum size is ~60KB. Please choose a smaller image.");
                    return;
                }

                String encoded = Base64.getEncoder().encodeToString(bytes);
                String message = "image:" + encoded;
                byte[] sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                socket.send(sendPacket);
            } catch (IOException ex) {
                if (ex instanceof SocketException && ex.getMessage().contains("larger than")) {
                    JOptionPane.showMessageDialog(comps.frame, "Image is too large to send. Please choose a smaller image (under ~60KB).");
                } else {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static void sendFile(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(comps.frame) == JFileChooser.APPROVE_OPTION) {
            try {
                byte[] bytes = Files.readAllBytes(chooser.getSelectedFile().toPath());
                String fileName = chooser.getSelectedFile().getName();

                // Check file size (UDP limit is ~65KB, leave buffer)
                if (bytes.length > 60000) {
                    JOptionPane.showMessageDialog(comps.frame, "File is too large to send. Maximum size is ~60KB. Please choose a smaller file.");
                    return;
                }

                String encoded = Base64.getEncoder().encodeToString(bytes);
                String message = "file:" + fileName + ":" + encoded;
                byte[] sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                socket.send(sendPacket);
            } catch (IOException ex) {
                if (ex instanceof SocketException && ex.getMessage().contains("larger than")) {
                    JOptionPane.showMessageDialog(comps.frame, "File is too large to send. Please choose a smaller file (under ~60KB).");
                } else {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static void recordVoice(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        if (!comps.isRecording) {
            // Start recording
            try {
                AudioFormat format = new AudioFormat(8000, 8, 1, true, true); // Lower quality: 8kHz, 8-bit, mono
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                comps.microphone = (TargetDataLine) AudioSystem.getLine(info);
                comps.microphone.open(format);
                comps.microphone.start();

                comps.isRecording = true;
                comps.recordVoiceButton.setText("Recording... (6s max)");

                // Auto-stop timer (6 seconds)
                comps.recordingTimer = new Timer(6000, e -> {
                    if (comps.isRecording) {
                        stopAndSendRecording(comps, socket, serverAddress, serverPort);
                    }
                });
                comps.recordingTimer.setRepeats(false);
                comps.recordingTimer.start();

                Thread recordingThread = new Thread(() -> {
                    comps.audioBuffer.reset();
                    byte[] buffer = new byte[512]; // Smaller buffer
                    while (comps.isRecording) {
                        int bytesRead = comps.microphone.read(buffer, 0, buffer.length);
                        comps.audioBuffer.write(buffer, 0, bytesRead);
                    }
                });
                recordingThread.start();

            } catch (LineUnavailableException e) {
                JOptionPane.showMessageDialog(comps.frame, "Microphone not available.");
            }
        } else {
            // Manual stop
            stopAndSendRecording(comps, socket, serverAddress, serverPort);
        }
    }

    private static void stopAndSendRecording(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        comps.isRecording = false;
        comps.recordVoiceButton.setText("Record Voice");
        if (comps.recordingTimer != null) {
            comps.recordingTimer.stop();
        }
        comps.microphone.stop();
        comps.microphone.close();

        try {
            byte[] audioBytes = comps.audioBuffer.toByteArray();
            // Check if audio is too large (UDP limit is ~65KB)
            if (audioBytes.length > 60000) { // Leave some buffer
                JOptionPane.showMessageDialog(comps.frame, "Voice message too long. Please record a shorter message (max 5-6 seconds).");
                return;
            }
            String encoded = Base64.getEncoder().encodeToString(audioBytes);
            String message = "voice:" + encoded;
            byte[] sendData = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            socket.send(sendPacket);
        } catch (IOException ex) {
            ex.printStackTrace();
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

    private static User findUserByName(DefaultListModel<User> model, String name) {
        for (int i = 0; i < model.getSize(); i++) {
            User user = model.getElementAt(i);
            if (user.name.equals(name)) {
                return user;
            }
        }
        return null;
    }

    private static void handleTyping(String response, GUIComponents comps) {
        String typer = response.substring(7);
        System.out.println(typer);
        if (!typer.equals(comps.userName)) {
            SwingUtilities.invokeLater(() -> comps.typingLabel.setText(typer + " is typing..."));
            if (comps.typingTimer != null) {
                comps.typingTimer.stop();
            }
            comps.typingTimer = new Timer(3000, e -> SwingUtilities.invokeLater(() -> comps.typingLabel.setText("")));
            comps.typingTimer.setRepeats(false);
            comps.typingTimer.start();
        }
    }

    private static void handleFileReceive(String response, GUIComponents comps) {
        String[] parts = response.split(":", 4);
        if (parts.length == 4) {
            String sender = parts[1];
            String fileName = parts[2];
            String base64 = parts[3];
            byte[] bytes = Base64.getDecoder().decode(base64);

            SwingUtilities.invokeLater(() -> {
                try {
                    comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(),
                        timeStamp() + sender + " sent a file: " + fileName + " (Click to download)\n", null);

                    // Add clickable link for download
                    JButton downloadButton = new JButton("Download " + fileName);
                    downloadButton.addActionListener(e -> saveFile(fileName, bytes, comps.frame));
                    comps.textPane.insertComponent(downloadButton);
                    comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), "\n", null);

                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static void handleVoiceReceive(String response, GUIComponents comps) {
        String[] parts = response.split(":", 3);
        if (parts.length == 3) {
            String sender = parts[1];
            String base64 = parts[2];
            byte[] audioBytes = Base64.getDecoder().decode(base64);

            SwingUtilities.invokeLater(() -> {
                try {
                    comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(),
                        timeStamp() + sender + " sent a voice message: ", null);

                    // Add play button for voice message
                    JButton playButton = new JButton("▶ Play Voice");
                    playButton.addActionListener(e -> playVoice(audioBytes, comps.frame));
                    comps.textPane.insertComponent(playButton);
                    comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), "\n", null);

                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static void saveFile(String fileName, byte[] bytes, JFrame frame) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(fileName));
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(chooser.getSelectedFile().toPath(), bytes);
                JOptionPane.showMessageDialog(frame, "File saved successfully!");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Error saving file: " + e.getMessage());
            }
        }
    }

    private static void playVoice(byte[] audioBytes, JFrame frame) {
        try {
            AudioFormat format = new AudioFormat(8000, 8, 1, true, true); // Match recording format
            AudioInputStream audioStream = new AudioInputStream(new ByteArrayInputStream(audioBytes), format, audioBytes.length / format.getFrameSize());
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();

            // Auto-close after playing
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });

        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error playing voice message: " + e.getMessage());
        }
    }

    private static void changeStatus(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        String[] options = {"Online", "Invisible", "Away", "Busy"};
        String selected = (String) JOptionPane.showInputDialog(
            comps.frame,
            "Choose your status:",
            "Change Status",
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );

        if (selected != null) {
            String status = selected.toLowerCase();
            comps.currentStatus = status;
            comps.statusButton.setText("Status: " + selected);

            // Save to preferences
            Preferences prefs = Preferences.userNodeForPackage(Client.class);
            prefs.put("userStatus", status);

            // Send status change to server
            try {
                String message = "status:" + status;
                byte[] sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                socket.send(sendPacket);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static String loadSavedStatus() {
        Preferences prefs = Preferences.userNodeForPackage(Client.class);
        return prefs.get("userStatus", "online");
    }
}