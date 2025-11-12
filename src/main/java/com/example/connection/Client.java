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
import javax.swing.text.html.HTMLEditorKit; // For HTML editing in JTextPane
import javax.swing.text.html.HTMLDocument; // For HTML document manipulation
import javax.swing.text.html.StyleSheet; // For styling HTML in JTextPane
import javax.swing.BorderFactory; // For creating borders

public class Client { // Main class for the UDP chat client application

    public static void main(String[] args) { // Entry point of the application, accepts command line arguments (not used here)
        // SwingUtilities.invokeLater ensures GUI updates happen on the Event Dispatch Thread (EDT).
        // This is required for Swing components to work properly and avoid threading issues.
        // We can't simplify it further because Swing is not thread-safe; all GUI operations must be on EDT.
        SwingUtilities.invokeLater(() -> { // Run the GUI initialization on the EDT

            // Get the user's name using the extracted method
            String name = getUserName(); // Prompt user for name and store it
            if (name == null) { // If no name provided (user canceled)
                return; // Exit if no name provided
            }

            try { // Start try block for network operations
                DatagramSocket socket = new DatagramSocket(); // Create a UDP socket with auto-assigned port
                InetAddress serverAddress = InetAddress.getByName("localhost"); // Resolve server IP address for communication
                int serverPort = 1234; // Define the server port number

                // Create GUI using the extracted method
                GUIComponents comps = createGUI(name); // Initialize and return GUI components

                // Add typing listener
                comps.textField.addKeyListener(new KeyAdapter() { // Attach key listener to text field for typing events
                    @Override
                    public void keyTyped(KeyEvent e) { // Called when a key is typed
                        if (!comps.textField.getText().trim().isEmpty() && System.currentTimeMillis() - comps.lastTypingTime > 1000) { // Check if text is not empty and 1 second has passed since last typing
                            sendTyping(comps, socket, serverAddress, serverPort); // Send typing indicator to server
                            comps.lastTypingTime = System.currentTimeMillis(); // Update last typing time
                        }
                    }
                });

                // Start receiving thread
                // A thread is like a separate worker that runs code in parallel to the main program.
                // We need a thread for receiving because UDP socket.receive() blocks (waits) until a message arrives.
                // If we did this in the main thread, the GUI would freeze and not respond to user clicks.
                // So, we run receiving in a background thread to keep the GUI responsive.
                Thread receiver = new Thread(() -> { // Create a new thread for receiving messages
                    try { // Try block for receiving loop
                        while (!Thread.currentThread().isInterrupted()) { // Loop while thread is not interrupted
                            byte[] receiveData = new byte[65535]; // Buffer for incoming data (max UDP packet size)
                            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); // Create packet for receiving
                            socket.receive(receivePacket); // Block and wait for incoming packet
                            String response = new String(receivePacket.getData(), 0, receivePacket.getLength()); // Convert received bytes to string
                            // Follow server structure: use switch with handlers
                            handleReceive(response, comps); // Process the received message based on type
                        }
                    } catch (IOException e) { // Catch IO exceptions (e.g., socket closed)
                        // Socket closed
                    }
                });
                receiver.start(); // Start the receiving thread

                // Load saved status and set initial status
                String savedStatus = loadSavedStatus(); // Retrieve previously saved user status from preferences
                comps.statusButton.setText("Status: " + savedStatus.substring(0, 1).toUpperCase() + savedStatus.substring(1)); // Update button text with capitalized status

                // Send login
                byte[] loginData = ("login:" + name).getBytes(); // Prepare login message as bytes
                DatagramPacket loginPacket = new DatagramPacket(loginData, loginData.length, serverAddress, serverPort); // Create login packet
                socket.send(loginPacket); // Send login packet to server

                // Send initial status
                String statusMessage = "status:" + savedStatus; // Prepare status message
                byte[] statusData = statusMessage.getBytes(); // Convert to bytes
                DatagramPacket statusPacket = new DatagramPacket(statusData, statusData.length, serverAddress, serverPort); // Create status packet
                socket.send(statusPacket); // Send initial status to server

                // Send action
                // ActionListener is an interface that lets us respond to events like button clicks.
                // We use it explicitly because GUI events are event-driven; the system calls our code when the event happens,
                // rather than us constantly checking (polling), which would waste CPU and make the app unresponsive.
                ActionListener sendAction = e -> sendMessage(comps, socket, serverAddress, serverPort); // Define action for sending messages
                comps.sendButton.addActionListener(sendAction); // Attach send action to send button
                comps.textField.addActionListener(sendAction); // Attach send action to text field (for Enter key)

                // Send image action
                comps.sendImageButton.addActionListener(e -> sendImage(comps, socket, serverAddress, serverPort)); // Attach image send action to button

                // Send file action
                comps.sendFileButton.addActionListener(e -> sendFile(comps, socket, serverAddress, serverPort)); // Attach file send action to button

                // Record voice action
                comps.recordVoiceButton.addActionListener(e -> recordVoice(comps, socket, serverAddress, serverPort)); // Attach voice recording action to button

                // Status change action
                comps.statusButton.addActionListener(e -> changeStatus(comps, socket, serverAddress, serverPort)); // Attach status change action to button

                // Logout action
                comps.logoutButton.addActionListener(e -> logout(socket, receiver, serverAddress, serverPort, comps.frame)); // Attach logout action to button

                // On close, handle logout
                handleWindowClose(comps.frame, socket, receiver, serverAddress, serverPort); // Set up window close handler for logout

            } catch (IOException ex) { // Catch any IO exceptions during setup
                ex.printStackTrace(); // Print stack trace for debugging
            }
        });
    }
    private static String getUserName() { // Prompt user for name input
        String name = JOptionPane.showInputDialog(null, "Enter your name:", "Login", JOptionPane.QUESTION_MESSAGE); // Show dialog
        if (name == null || name.trim().isEmpty()) { // If canceled or empty
            return null; // Return null
        }
        return name.trim(); // Return trimmed name
    }

    private static class GUIComponents { // Inner class to hold all GUI-related components and state variables
        JFrame frame; // Main application window
        JTextPane textPane; // Text area for displaying chat messages in HTML format
        DefaultListModel<User> model; // Model for the user list, holds User objects
        JList<User> list; // List component displaying online users
        JTextField textField; // Input field for typing messages
        JButton sendButton; // Button to send text messages
        JButton sendImageButton; // Button to send image files
        JButton sendFileButton; // Button to send general files
        JButton recordVoiceButton; // Button to record and send voice messages
        JButton statusButton; // Button to change user status
        JButton logoutButton; // Button to logout and close the application
        JLabel typingLabel; // Label showing typing indicators from other users
        String userName; // Current user's name
        Timer typingTimer; // Timer for hiding typing indicators after inactivity
        long lastTypingTime; // Timestamp of last typing event to throttle sending
        boolean isRecording; // Flag indicating if voice recording is in progress
        TargetDataLine microphone; // Audio input line for recording voice
        ByteArrayOutputStream audioBuffer; // Buffer to store recorded audio data
        Timer recordingTimer; // Timer to auto-stop recording after max duration
    }

    private static class User { // Inner class representing a user in the chat system
        String name; // User's display name
        String status; // User's current status (online, away, busy, invisible, offline)

        User(String name, String status) { // Constructor to initialize user with name and status
            this.name = name; // Set the user's name
            this.status = status; // Set the user's status
        }

        boolean isVisible() { // Method to check if the user should be visible in the user list
            return !"invisible".equals(status); // Return true if status is not invisible
        }
    }

    /**
     * Icon implementation that draws an anti-aliased filled circle.
     */
    private static class CircleIcon implements Icon { // Custom icon class for drawing circular status indicators
        private final Color color; // Color of the circle
        private final int size; // Diameter of the circle

        CircleIcon(Color color, int size) { // Constructor for CircleIcon
            this.color = color == null ? Color.GRAY : color; // Set color, default to gray if null
            this.size = size; // Set the size
        }

        @Override
        public int getIconWidth() { // Return the width of the icon
            return size; // Width equals size
        }

        @Override
        public int getIconHeight() { // Return the height of the icon
            return size; // Height equals size
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) { // Draw the icon at specified position
            Graphics2D g2 = (Graphics2D) g.create(); // Create a copy of Graphics2D for safe modification
            try { // Try block to ensure disposal
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Enable anti-aliasing for smooth edges
                g2.setColor(color); // Set fill color
                g2.fillOval(x, y, size, size); // Draw filled circle
                g2.setColor(color.darker()); // Set darker color for border
                g2.drawOval(x, y, size - 1, size - 1); // Draw border circle
            } finally { // Ensure graphics context is disposed
                g2.dispose(); // Dispose to free resources
            }
        }
    }

    /**
     * Renders a user row with a small colored circle icon to represent status.
     * This avoids relying on emoji rendering which can look inconsistent across platforms.
     */
    private static class UserCellRenderer extends DefaultListCellRenderer { // Custom renderer for user list items
        private final int iconSize = 12; // Size of the status icon

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) { // Customize the rendering of each list cell
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus); // Call parent method for default setup
            if (value instanceof User) { // Check if the value is a User object
                User user = (User) value; // Cast to User
                setText(user.name); // Set the text to user's name
                setIcon(new CircleIcon(getStatusColor(user.status), iconSize)); // Set icon based on status color
                setIconTextGap(8); // Set gap between icon and text
            } else { // If not a User
                setIcon(null); // No icon
            }
            return this; // Return the component
        }

        private Color getStatusColor(String status) { // Get color corresponding to status
            switch (status) { // Switch on status string
                case "online": return new Color(0x1DB954); // pleasant green
                case "invisible": return Color.LIGHT_GRAY; // Light gray for invisible
                case "away": return new Color(0xFFC107); // amber
                case "busy": return new Color(0xD32F2F); // red
                default: return Color.GRAY; // Default gray
            }
        }
    }

    private static GUIComponents createGUI(String name) { // Method to create and configure all GUI components
        GUIComponents comps = new GUIComponents(); // Instantiate the GUI components holder
        comps.userName = name; // Set the username in components
        comps.lastTypingTime = System.currentTimeMillis() - 2000; // Allow immediate first send (set to past time)

        comps.frame = new JFrame("UDP Chat Client - " + name); // Create main window with title including username
        comps.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Exit application on window close
        comps.frame.setSize(800, 400); // Set window size
        comps.frame.getContentPane().setBackground(Color.WHITE); // Set background color to white

        comps.textPane = new JTextPane(); // Create text pane for chat messages
        comps.textPane.setContentType("text/html"); // Set content type to HTML for rich text
        HTMLEditorKit kit = new HTMLEditorKit(); // Create HTML editor kit
        comps.textPane.setEditorKit(kit); // Set the editor kit for HTML support
        StyleSheet styleSheet = kit.getStyleSheet(); // Get the style sheet
        styleSheet.addRule("body { margin: 0; padding: 0; background-color: white; }"); // Add CSS rule for body
        comps.textPane.setBackground(Color.WHITE); // Set background to white
        comps.textPane.setBorder(null); // Remove border
        comps.textPane.setText("<html><body style='margin:0; padding:0; background-color:white;'></body></html>"); // Initialize with empty HTML body
        comps.typingLabel = new JLabel(""); // Create label for typing indicator
        comps.typingLabel.setForeground(Color.GRAY); // Set text color to gray
        comps.typingLabel.setVisible(false); // Initially hide the label
        comps.typingLabel.setFont(new Font("Arial", Font.ITALIC, 12)); // Set font to italic Arial
        comps.typingLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10)); // Add padding
        comps.typingLabel.setBackground(new Color(240, 240, 240)); // Set light gray background
        comps.typingLabel.setOpaque(true); // Make background visible
        comps.textPane.setEditable(false); // Make text pane read-only

        JPanel chatPanel = new JPanel(new BorderLayout()); // Create panel for chat area
        chatPanel.setBackground(Color.WHITE); // Set background to white
        chatPanel.add(comps.typingLabel, BorderLayout.NORTH); // Add typing label at top
        JScrollPane scrollPane = new JScrollPane(comps.textPane); // Create scroll pane for text pane
        scrollPane.getViewport().setBackground(Color.WHITE); // Set viewport background
        scrollPane.setBorder(null); // Remove border
        chatPanel.add(scrollPane, BorderLayout.CENTER); // Add scroll pane to center
        JScrollPane chatScrollPane = new JScrollPane(chatPanel); // Create outer scroll pane

        comps.model = new DefaultListModel<>(); // Create model for user list
        comps.list = new JList<>(comps.model); // Create list component with model
        comps.list.setCellRenderer(new UserCellRenderer()); // Set custom renderer
        comps.list.setBackground(Color.WHITE); // Set background to white
        JScrollPane listScrollPane = new JScrollPane(comps.list); // Create scroll pane for list
        listScrollPane.getViewport().setBackground(Color.WHITE); // Set viewport background
        listScrollPane.setBorder(null); // Remove border

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScrollPane, listScrollPane); // Create split pane
        splitPane.setDividerLocation(400); // Set initial divider position
        splitPane.setResizeWeight(0.75); // Set resize weight
        splitPane.setBackground(Color.WHITE); // Set background
        splitPane.setOpaque(true); // Make background opaque

        comps.textField = new JTextField(); // Create text field for input
        comps.textField.setPreferredSize(new Dimension(200, 25)); // Fix width issue (set preferred size)
        comps.sendButton = new JButton("Send"); // Create send button
        comps.sendImageButton = new JButton("Send Image"); // Create send image button
        comps.sendFileButton = new JButton("Send File"); // Create send file button
        comps.recordVoiceButton = new JButton("Record Voice"); // Create record voice button
        comps.statusButton = new JButton("Status: Online"); // Create status button
        comps.logoutButton = new JButton("Logout"); // Create logout button
        comps.isRecording = false; // Initialize recording flag to false
        comps.audioBuffer = new ByteArrayOutputStream(); // Create buffer for audio data

        JPanel panel = new JPanel(new BorderLayout()); // Create main panel
        panel.setBackground(Color.WHITE); // Set background
        panel.add(splitPane, BorderLayout.CENTER); // Add split pane to center

        JPanel bottomPanel = new JPanel(new FlowLayout()); // Create bottom panel for buttons
        bottomPanel.setBackground(Color.WHITE); // Set background
        bottomPanel.add(comps.textField); // Add text field
        bottomPanel.add(comps.sendButton); // Add send button
        bottomPanel.add(comps.sendImageButton); // Add send image button
        bottomPanel.add(comps.sendFileButton); // Add send file button
        bottomPanel.add(comps.recordVoiceButton); // Add record voice button
        bottomPanel.add(comps.statusButton); // Add status button
        bottomPanel.add(comps.logoutButton); // Add logout button

        panel.add(bottomPanel, BorderLayout.SOUTH); // Add bottom panel to south
        comps.frame.add(panel); // Add main panel to frame
        comps.frame.setVisible(true); // Make frame visible

        return comps; // Return the configured components
    }

    // handlers
    private static void handleJoined(String response, GUIComponents comps) { // Handle user joined event from server
        String name = response.substring(7); // Extract username from "joined:name"
        SwingUtilities.invokeLater(() -> { // Update GUI on EDT
            User user = findUserByName(comps.model, name); // Check if user already exists
            if (user == null) { // If new user
                comps.model.addElement(new User(name, "online")); // Add new user as online
            } else { // If existing user
                user.status = "online"; // Update status to online
                comps.list.repaint(); // Repaint list to show changes
            }
        });
    }

    private static void handleLeft(String response, GUIComponents comps) { // Handle user left event from server
        String name = response.substring(5); // Extract username from "left:name"
        SwingUtilities.invokeLater(() -> { // Update GUI on EDT
            User user = findUserByName(comps.model, name); // Find the user
            if (user != null) { // If user exists
                user.status = "offline"; // Set status to offline
                comps.list.repaint(); // Repaint list
            }
        });
    }

    private static void handleList(String response, GUIComponents comps) { // Handle user list update from server
    String[] parts = response.substring(5).split(","); // Split "list:name:status,name:status,..." into parts
    SwingUtilities.invokeLater(() -> { // Update GUI on EDT
        comps.model.clear(); // Clear current list
        for (String part : parts) { // Iterate over each user entry
            if (!part.isEmpty()) { // Skip empty parts
                String[] sub = part.split(":"); // Split into name and status
                if (sub.length == 2) { // Ensure valid format
                    String name = sub[0]; // Extract name
                    String status = sub[1]; // Extract status
                    // Show all users, including self, if visible
                    User user = new User(name, status); // Create user object
                    if (user.isVisible()) { // Check if visible
                        comps.model.addElement(user); // Add to list
                    }
                }
            }
        }
    });
}

    private static void handleStatusUpdate(String response, GUIComponents comps) { // Handle status update from server
        String[] parts = response.split(":"); // Split "status:username:newstatus"
        if (parts.length == 3 && "status".equals(parts[0])) { // Validate format
            String userName = parts[1]; // Extract username
            String newStatus = parts[2]; // Extract new status
            SwingUtilities.invokeLater(() -> { // Update GUI on EDT
                User user = findUserByName(comps.model, userName); // Find user
                if (user != null) { // If user exists
                    user.status = newStatus; // Update status
                    if (!user.isVisible()) { // If now invisible
                        comps.model.removeElement(user); // Remove from list
                    }
                    comps.list.repaint(); // Repaint list
                } else if (!"invisible".equals(newStatus) && !"offline".equals(newStatus)) { // If new visible user
                    // Add user if they become visible
                    comps.model.addElement(new User(userName, newStatus)); // Add to list
                }
            });
        }
    }


    // Image sending/receiving/displaying:
    // Sending: Read image file as bytes, encode to Base64 string, send as "image:base64string"
    // Receiving: Decode Base64 back to bytes, use ImageIO.read to create BufferedImage from ByteArrayInputStream
    // Displaying: Create ImageIcon from BufferedImage, insert into JTextPane document like text
    private static void handleImageReceive(String response, GUIComponents comps) { // Handle received image message
        String[] parts = response.split(":", 3); // Split "image:sender:base64"
        if (parts.length == 3) { // Validate format
            String sender = parts[1]; // Extract sender name
            if (sender.equals(comps.userName)) return; // Skip own images received back from server
            String base64 = parts[2]; // Extract base64 data
            byte[] bytes = Base64.getDecoder().decode(base64); // Decode to bytes
            displayImageLocally(comps, sender, bytes); // Display the image
        }
    }

    private static void handleMessage(String response, GUIComponents comps) { // Handle text message from server
        String[] parts = response.split(":", 2); // Split "sender:message"
        if (parts.length == 2) { // If valid format
            String sender = parts[0]; // Extract sender
            String originalMessage = parts[1]; // Extract message
            if (sender.equals(comps.userName)) return; // Skip own messages received back from server
            boolean isPrivate = originalMessage.startsWith("(private) "); // Check if private
            String message = isPrivate ? originalMessage.substring(10) : originalMessage; // Remove private prefix if present
            SwingUtilities.invokeLater(() -> insertMessage(comps, sender, message, false, isPrivate)); // Insert message in GUI
        } else { // If invalid format
            // fallback
            SwingUtilities.invokeLater(() -> { // Update GUI
                HTMLEditorKit kit = (HTMLEditorKit) comps.textPane.getEditorKit(); // Get kit
                HTMLDocument doc = (HTMLDocument) comps.textPane.getDocument(); // Get doc
                try { // Insert raw response
                    kit.insertHTML(doc, doc.getLength(), "<div>" + timeStamp() + response + "</div>", 0, 0, null); // Insert with timestamp
                } catch (BadLocationException | IOException e) { // Handle
                    e.printStackTrace(); // Print
                }
            });
        }
    }

    // Return a short timestamp like [14:32] to prefix messages
    private static String timeStamp() { // Generate current time string for messages
        return "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "] "; // Format as [HH:mm]
    }

    private static void insertMessage(GUIComponents comps, String sender, String message, boolean isOwn, boolean isPrivate) { // Insert a message into the chat pane
        String align = isOwn ? "right" : "left"; // Align right for own messages, left for others
        String color = isOwn ? "#DCF8C6" : "#FFFFFF"; // light green for sent, white for received
        String borderColor = isPrivate ? "#FF6B6B" : "#CCCCCC"; // red border for private
        String html = "<div style='text-align:" + align + "; margin: 10px 15px; clear: both;'><div style='background-color:" + color + "; padding: 10px 15px; border-radius: 20px; display: inline-block; max-width: 70%; word-wrap: break-word; border: 2px solid " + borderColor + "; box-shadow: 0 2px 5px rgba(0,0,0,0.1);'>" + // HTML for message bubble with enhanced styling
                      "<b>" + sender + ":</b> " + message.replace("\n", "<br>") + "<br><small style='color:gray; font-size:10px; margin-top: 5px; display: block;'>" + timeStamp() + "</small></div></div>"; // Include sender, message, and timestamp with better spacing
        HTMLEditorKit kit = (HTMLEditorKit) comps.textPane.getEditorKit(); // Get HTML editor kit
        HTMLDocument doc = (HTMLDocument) comps.textPane.getDocument(); // Get document
        try { // Try to insert HTML
            kit.insertHTML(doc, doc.getLength(), html, 0, 0, null); // Insert at end of document
        } catch (BadLocationException | IOException e) { // Handle exceptions
            e.printStackTrace(); // Print error
        }
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

    private static void sendMessage(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) { // Send text message to server
        String message = comps.textField.getText().trim(); // Get and trim text from field
        if (!message.isEmpty()) { // If message is not empty
            User selected = comps.list.getSelectedValue(); // Get selected user for private message
            if (selected != null) { // If user selected
                message = "private:" + selected.name + ":" + message; // Format as private message
            }
            try { // Try to send
                byte[] sendData = message.getBytes(); // Convert to bytes
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort); // Create packet
                socket.send(sendPacket); // Send packet
                // Display sent message locally
                String displayMessage = message; // Copy message for display
                boolean isPrivate = selected != null; // Check if private
                if (isPrivate) { // If private
                    int firstColon = message.indexOf(":"); // Find first colon
                    int secondColon = message.indexOf(":", firstColon + 1); // Find second colon
                    displayMessage = message.substring(secondColon + 1); // Extract actual message
                }
                insertMessage(comps, comps.userName, displayMessage, true, isPrivate); // Insert sent message in GUI
                comps.textField.setText(""); // Clear text field
                // Typing indicator is at the top, no need to clear
            } catch (IOException ex) { // Handle IO exception
                ex.printStackTrace(); // Print error
            }
        }
    }

    private static void sendTyping(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) { // Send typing indicator to server
        try { // Try to send
            String message = "typing:" + comps.userName; // Format typing message
            byte[] sendData = message.getBytes(); // Convert to bytes
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort); // Create packet
            socket.send(sendPacket); // Send packet
        } catch (IOException ex) { // Handle IO exception
            ex.printStackTrace(); // Print error
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

                // Display image locally
                displayImageLocally(comps, comps.userName, bytes);
            } catch (IOException ex) {
                if (ex instanceof SocketException && ex.getMessage().contains("larger than")) {
                    JOptionPane.showMessageDialog(comps.frame, "Image is too large to send. Please choose a smaller image (under ~60KB).");
                } else {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static void sendFile(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) { // Send file to server
        JFileChooser chooser = new JFileChooser(); // Create file chooser
        if (chooser.showOpenDialog(comps.frame) == JFileChooser.APPROVE_OPTION) { // If file selected
            try { // Try to send
                byte[] bytes = Files.readAllBytes(chooser.getSelectedFile().toPath()); // Read file bytes
                String fileName = chooser.getSelectedFile().getName(); // Get filename

                // Check file size (UDP limit is ~65KB, leave buffer)
                if (bytes.length > 60000) { // If too large
                    JOptionPane.showMessageDialog(comps.frame, "File is too large to send. Maximum size is ~60KB. Please choose a smaller file."); // Show error
                    return; // Exit
                }

                String encoded = Base64.getEncoder().encodeToString(bytes); // Encode to base64
                String message = "file:" + fileName + ":" + encoded; // Format message
                byte[] sendData = message.getBytes(); // Convert to bytes
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort); // Create packet
                socket.send(sendPacket); // Send packet
            } catch (IOException ex) { // Handle IO exception
                if (ex instanceof SocketException && ex.getMessage().contains("larger than")) { // If size issue
                    JOptionPane.showMessageDialog(comps.frame, "File is too large to send. Please choose a smaller file (under ~60KB)."); // Show error
                } else { // Other error
                    ex.printStackTrace(); // Print
                }
            }
        }
    }

    private static void recordVoice(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) { // Handle voice recording
        if (!comps.isRecording) { // If not recording
            // Start recording
            try { // Try to start recording
                AudioFormat format = new AudioFormat(8000, 8, 1, true, true); // Lower quality: 8kHz, 8-bit, mono
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format); // Info for microphone
                comps.microphone = (TargetDataLine) AudioSystem.getLine(info); // Get microphone line
                comps.microphone.open(format); // Open with format
                comps.microphone.start(); // Start recording

                comps.isRecording = true; // Set flag
                comps.recordVoiceButton.setText("Recording... (6s max)"); // Update button text

                // Auto-stop timer (6 seconds)
                comps.recordingTimer = new Timer(6000, e -> { // Timer for auto-stop
                    if (comps.isRecording) { // If still recording
                        stopAndSendRecording(comps, socket, serverAddress, serverPort); // Stop and send
                    }
                });
                comps.recordingTimer.setRepeats(false); // One time
                comps.recordingTimer.start(); // Start timer

                Thread recordingThread = new Thread(() -> { // Thread for recording
                    comps.audioBuffer.reset(); // Reset buffer
                    byte[] buffer = new byte[512]; // Smaller buffer
                    while (comps.isRecording) { // While recording
                        int bytesRead = comps.microphone.read(buffer, 0, buffer.length); // Read audio
                        comps.audioBuffer.write(buffer, 0, bytesRead); // Write to buffer
                    }
                });
                recordingThread.start(); // Start recording thread

            } catch (LineUnavailableException e) { // If microphone unavailable
                JOptionPane.showMessageDialog(comps.frame, "Microphone not available."); // Show error
            }
        } else { // If already recording
            // Manual stop
            stopAndSendRecording(comps, socket, serverAddress, serverPort); // Stop and send
        }
    }

    private static void stopAndSendRecording(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) { // Stop recording and send voice message
        comps.isRecording = false; // Set flag to false
        comps.recordVoiceButton.setText("Record Voice"); // Reset button text
        if (comps.recordingTimer != null) { // If timer exists
            comps.recordingTimer.stop(); // Stop timer
        }
        comps.microphone.stop(); // Stop microphone
        comps.microphone.close(); // Close microphone

        try { // Try to send
            byte[] audioBytes = comps.audioBuffer.toByteArray(); // Get audio bytes
            // Check if audio is too large (UDP limit is ~65KB)
            if (audioBytes.length > 60000) { // Leave some buffer
                JOptionPane.showMessageDialog(comps.frame, "Voice message too long. Please record a shorter message (max 5-6 seconds)."); // Show error
                return; // Exit
            }
            String encoded = Base64.getEncoder().encodeToString(audioBytes); // Encode to base64
            String message = "voice:" + encoded; // Format message
            byte[] sendData = message.getBytes(); // Convert to bytes
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort); // Create packet
            socket.send(sendPacket); // Send packet
        } catch (IOException ex) { // Handle IO exception
            ex.printStackTrace(); // Print error
        }
    }

    private static void handleWindowClose(JFrame frame, DatagramSocket socket, Thread receiver, InetAddress serverAddress, int serverPort) { // Set up window close handler
        frame.addWindowListener(new WindowAdapter() { // Add window listener
            @Override
            public void windowClosing(WindowEvent e) { // When window closing
                try { // Try to send logout
                    byte[] sendData = "logout".getBytes(); // Prepare logout message
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort); // Create packet
                    socket.send(sendPacket); // Send logout
                } catch (IOException ex) { // Handle IO exception
                    System.out.println("Error logging out"); // Print error
                }
                socket.close(); // Close socket
                receiver.interrupt(); // Interrupt receiver thread
            }
        });
    }

    private static void logout(DatagramSocket socket, Thread receiver, InetAddress serverAddress, int serverPort, JFrame frame) { // Logout and close application
        try { // Try to send logout
            byte[] sendData = "logout".getBytes(); // Prepare logout message
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort); // Create packet
            socket.send(sendPacket); // Send logout
        } catch (IOException ex) { // Handle IO exception
            // ignore
        }
        socket.close(); // Close socket
        receiver.interrupt(); // Interrupt receiver thread
        frame.dispose(); // Dispose frame
    }

    private static User findUserByName(DefaultListModel<User> model, String name) { // Find user by name in the list model
        for (int i = 0; i < model.getSize(); i++) { // Iterate through all users
            User user = model.getElementAt(i); // Get user at index
            if (user.name.equals(name)) { // If name matches
                return user; // Return the user
            }
        }
        return null; // Return null if not found
    }

    private static void handleTyping(String response, GUIComponents comps) { // Handle typing indicator from server
        String typer = response.substring(7); // Extract typer's name
        System.out.println(typer); // Print for debugging
        if (!typer.equals(comps.userName)) { // If not self
            SwingUtilities.invokeLater(() -> { // Update GUI on EDT
                comps.typingLabel.setText(typer + " is typing..."); // Set label text
                comps.typingLabel.setVisible(true); // Show label
            });
            if (comps.typingTimer != null) { // If timer exists
                comps.typingTimer.stop(); // Stop previous timer
            }
            comps.typingTimer = new Timer(3000, e -> SwingUtilities.invokeLater(() -> { // Create new timer
                comps.typingLabel.setText(""); // Clear text
                comps.typingLabel.setVisible(false); // Hide label
            }));
            comps.typingTimer.setRepeats(false); // One time
            comps.typingTimer.start(); // Start timer
        }
    }

    private static void handleFileReceive(String response, GUIComponents comps) { // Handle received file message
        String[] parts = response.split(":", 4); // Split "file:sender:filename:base64"
        if (parts.length == 4) { // Validate format
            String sender = parts[1]; // Extract sender
            String fileName = parts[2]; // Extract filename
            String base64 = parts[3]; // Extract base64
            byte[] bytes = Base64.getDecoder().decode(base64); // Decode to bytes

            SwingUtilities.invokeLater(() -> { // Update GUI on EDT
                try { // Try to insert
                    comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), // Insert text
                        timeStamp() + sender + " sent a file: " + fileName + " (Click to download)\n", null); // Text message

                    // Add clickable link for download
                    JButton downloadButton = new JButton("Download " + fileName); // Create download button
                    downloadButton.addActionListener(e -> saveFile(fileName, bytes, comps.frame)); // Add action to save file
                    comps.textPane.insertComponent(downloadButton); // Insert button
                    comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), "\n", null); // New line

                } catch (BadLocationException e) { // Handle exception
                    e.printStackTrace(); // Print error
                }
            });
        }
    }

    private static void handleVoiceReceive(String response, GUIComponents comps) { // Handle received voice message
        String[] parts = response.split(":", 3); // Split "voice:sender:base64"
        if (parts.length == 3) { // Validate format
            String sender = parts[1]; // Extract sender
            String base64 = parts[2]; // Extract base64
            byte[] audioBytes = Base64.getDecoder().decode(base64); // Decode to bytes

            SwingUtilities.invokeLater(() -> { // Update GUI on EDT
                try { // Try to insert
                    comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), // Insert text
                        timeStamp() + sender + " sent a voice message: ", null); // Text message

                    // Add play button for voice message
                    JButton playButton = new JButton("▶ Play Voice"); // Create play button
                    playButton.addActionListener(e -> playVoice(audioBytes, comps.frame)); // Add action to play voice
                    comps.textPane.insertComponent(playButton); // Insert button
                    comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), "\n", null); // New line

                } catch (BadLocationException e) { // Handle exception
                    e.printStackTrace(); // Print error
                }
            });
        }
    }

    private static void saveFile(String fileName, byte[] bytes, JFrame frame) { // Save file to disk
        JFileChooser chooser = new JFileChooser(); // Create file chooser
        chooser.setSelectedFile(new File(fileName)); // Set default filename
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) { // If user approves
            try { // Try to save
                Files.write(chooser.getSelectedFile().toPath(), bytes); // Write bytes to file
                JOptionPane.showMessageDialog(frame, "File saved successfully!"); // Success message
            } catch (IOException e) { // Handle IO exception
                JOptionPane.showMessageDialog(frame, "Error saving file: " + e.getMessage()); // Error message
            }
        }
    }

    private static void playVoice(byte[] audioBytes, JFrame frame) { // Play voice message
        try { // Try to play
            AudioFormat format = new AudioFormat(8000, 8, 1, true, true); // Match recording format
            AudioInputStream audioStream = new AudioInputStream(new ByteArrayInputStream(audioBytes), format, audioBytes.length / format.getFrameSize()); // Create audio stream
            Clip clip = AudioSystem.getClip(); // Get clip
            clip.open(audioStream); // Open with stream
            clip.start(); // Start playing

            // Auto-close after playing
            clip.addLineListener(event -> { // Add listener
                if (event.getType() == LineEvent.Type.STOP) { // When stopped
                    clip.close(); // Close clip
                }
            });

        } catch (Exception e) { // Handle exception
            JOptionPane.showMessageDialog(frame, "Error playing voice message: " + e.getMessage()); // Show error
        }
    }

    private static void changeStatus(GUIComponents comps, DatagramSocket socket, InetAddress serverAddress, int serverPort) { // Change user status
        String[] options = {"Online", "Invisible", "Away", "Busy"}; // Status options
        String selected = (String) JOptionPane.showInputDialog( // Show dialog
            comps.frame, // Parent frame
            "Choose your status:", // Message
            "Change Status", // Title
            JOptionPane.QUESTION_MESSAGE, // Message type
            null, // Icon
            options, // Options
            options[0] // Default
        );

        if (selected != null) { // If selected
            String status = selected.toLowerCase(); // Lowercase status
            comps.statusButton.setText("Status: " + selected); // Update button text

            // Save to preferences
            Preferences prefs = Preferences.userNodeForPackage(Client.class); // Get preferences
            prefs.put("userStatus", status); // Save status

            // Send status change to server
            try { // Try to send
                String message = "status:" + status; // Format message
                byte[] sendData = message.getBytes(); // Convert to bytes
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort); // Create packet
                socket.send(sendPacket); // Send packet
            } catch (IOException ex) { // Handle IO exception
                ex.printStackTrace(); // Print error
            }
        }
    }

    private static String loadSavedStatus() { // Load saved status from preferences
        Preferences prefs = Preferences.userNodeForPackage(Client.class); // Get preferences
        return prefs.get("userStatus", "online"); // Get status, default to online
    }

    private static void displayImageLocally(GUIComponents comps, String sender, byte[] bytes) { // Display image in chat
        try { // Try to read image
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes)); // Create image from bytes
            if (image != null) { // If valid image
                SwingUtilities.invokeLater(() -> { // Update GUI on EDT
                    try { // Try to insert components
                        // Insert text
                        comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), timeStamp() + sender + " sent an image:\n", null); // Insert text
                        // Create label with image
                        JLabel imageLabel = new JLabel(new ImageIcon(image)); // Create label with image icon
                        imageLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); // Add padding
                        comps.textPane.insertComponent(imageLabel); // Insert image component
                        comps.textPane.getDocument().insertString(comps.textPane.getDocument().getLength(), "\n", null); // New line
                    } catch (BadLocationException e) { // Handle
                        e.printStackTrace(); // Print
                    }
                });
            } else { // If invalid image
                SwingUtilities.invokeLater(() -> { // Update GUI
                    String html = "<div style='text-align:left; margin:10px 15px; clear: both;'><div style='background-color:#FFFFFF; padding:10px 15px; border-radius:20px; display:inline-block; max-width:70%; border:2px solid #CCCCCC; box-shadow: 0 2px 5px rgba(0,0,0,0.1);'>" + // Error message HTML with enhanced styling
                                      "<b>" + sender + " sent an invalid image.</b><br><small style='color:gray; font-size:10px; margin-top: 5px; display: block;'>" + timeStamp() + "</small></div></div>"; // Error text with better spacing
                    HTMLEditorKit kit = (HTMLEditorKit) comps.textPane.getEditorKit(); // Get kit
                    HTMLDocument doc = (HTMLDocument) comps.textPane.getDocument(); // Get doc
                    try { // Insert error HTML
                        kit.insertHTML(doc, doc.getLength(), html, 0, 0, null); // Insert
                    } catch (BadLocationException | IOException e) { // Handle
                        e.printStackTrace(); // Print
                    }
                });
            }
        } catch (IOException ioEx) { // If IO exception
            SwingUtilities.invokeLater(() -> { // Update GUI
                String html = "<div style='text-align:left; margin:10px 15px; clear: both;'><div style='background-color:#FFFFFF; padding:10px 15px; border-radius:20px; display:inline-block; max-width:70%; border:2px solid #CCCCCC; box-shadow: 0 2px 5px rgba(0,0,0,0.1);'>" + // Error HTML with enhanced styling
                                  "<b>" + sender + " sent an invalid image.</b><br><small style='color:gray; font-size:10px; margin-top: 5px; display: block;'>" + timeStamp() + "</small></div></div>"; // Error text with better spacing
                HTMLEditorKit kit = (HTMLEditorKit) comps.textPane.getEditorKit(); // Get kit
                HTMLDocument doc = (HTMLDocument) comps.textPane.getDocument(); // Get doc
                try { // Insert
                    kit.insertHTML(doc, doc.getLength(), html, 0, 0, null); // Insert
                } catch (BadLocationException | IOException e) { // Handle
                    e.printStackTrace(); // Print
                }
            });
        }
    }
}