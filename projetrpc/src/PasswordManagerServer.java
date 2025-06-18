import javax.net.ssl.*;
import java.security.KeyStore;
import javax.swing.Timer; // Remplacer l'import ambigu
import java.util.Date;    // Remplacer l'import ambigu
import java.util.List;   // Remplacer l'import ambigu
import java.io.*;
import java.net.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Base64;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;

public class PasswordManagerServer {
    private static final int PORT = 8080;
    private static final String DB_URL = "jdbc:sqlite:passwords.db";
    private ServerSocket serverSocket;
    private boolean running;
    private ExecutorService threadPool;
    private Map<String, SessionInfo> activeSessions;

    // Interface graphique
    private JFrame mainFrame;
    private JLabel statusLabel;
    private JLabel clientCountLabel;
    private JLabel activeSessionsLabel;
    private DefaultTableModel sessionsTableModel;
    private DefaultTableModel clientsTableModel;
    private JTextArea logArea;
    private JButton startStopButton;

    // Statistiques
    private int totalConnections = 0;
    private long serverStartTime;
    private SSLContext createSSLContext() throws Exception {
        char[] password = "changeit".toCharArray(); // Mot de passe du keystore
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new FileInputStream("passwordmanager.p12"), password);

        KeyManagerFactory kmf = KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    // Classe pour stocker les informations de session
    private static class SessionInfo {
        String username;
        String clientIP;
        long loginTime;
        long lastActivity;

        SessionInfo(String username, String clientIP) {
            this.username = username;
            this.clientIP = clientIP;
            this.loginTime = System.currentTimeMillis();
            this.lastActivity = System.currentTimeMillis();
        }

        void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
        }
    }

    // Classe pour stocker les informations de client
    private static class ClientInfo {
        String clientIP;
        int clientPort;
        long connectionTime;
        boolean isActive;

        ClientInfo(String clientIP, int clientPort) {
            this.clientIP = clientIP;
            this.clientPort = clientPort;
            this.connectionTime = System.currentTimeMillis();
            this.isActive = true;
        }
    }

    private Set<ClientInfo> connectedClients = ConcurrentHashMap.newKeySet();

    public PasswordManagerServer() throws IOException {
        activeSessions = new ConcurrentHashMap<>();
        threadPool = Executors.newFixedThreadPool(10);
        initializeDatabase();
        createGUI();
        serverStartTime = System.currentTimeMillis();
    }

    private void createGUI() {
        mainFrame = new JFrame("Serveur Gestionnaire de Mots de Passe");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(900, 700);
        mainFrame.setLocationRelativeTo(null);

        // Panel principal
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(240, 248, 255));

        // Panel du haut - Informations générales
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Panel central - Onglets
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(240, 248, 255));

        // Onglet Sessions actives
        JPanel sessionsPanel = createSessionsPanel();
        tabbedPane.addTab("Sessions Actives", new ImageIcon(), sessionsPanel, "Voir les sessions utilisateurs actives");

        // Onglet Clients connectés
        JPanel clientsPanel = createClientsPanel();
        tabbedPane.addTab("Clients Connectés", new ImageIcon(), clientsPanel, "Voir les clients connectés");

        // Onglet Logs
        JPanel logsPanel = createLogsPanel();
        tabbedPane.addTab("Logs du Serveur", new ImageIcon(), logsPanel, "Voir les logs du serveur");

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Panel du bas - Contrôles
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        mainFrame.add(mainPanel);
        mainFrame.setVisible(true);

        // Timer pour mettre à jour l'interface toutes les 2 secondes
        Timer uiUpdateTimer = new Timer(2000, e -> updateGUI());
        uiUpdateTimer.start();

        addLog("Interface graphique initialisée");
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new GridLayout(1, 4, 10, 10));
        topPanel.setBorder(BorderFactory.createTitledBorder("État du Serveur"));
        topPanel.setBackground(new Color(240, 248, 255));

        statusLabel = new JLabel("État: Arrêté", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 12));

        clientCountLabel = new JLabel("Clients: 0", SwingConstants.CENTER);
        clientCountLabel.setFont(new Font("Arial", Font.BOLD, 12));

        activeSessionsLabel = new JLabel("Sessions: 0", SwingConstants.CENTER);
        activeSessionsLabel.setFont(new Font("Arial", Font.BOLD, 12));

        JLabel portLabel = new JLabel("Port: " + PORT, SwingConstants.CENTER);
        portLabel.setFont(new Font("Arial", Font.BOLD, 12));

        topPanel.add(statusLabel);
        topPanel.add(clientCountLabel);
        topPanel.add(activeSessionsLabel);
        topPanel.add(portLabel);

        return topPanel;
    }

    private JPanel createSessionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 248, 255));

        String[] sessionColumns = {"Nom d'utilisateur", "IP Client", "Heure de connexion", "Dernière activité", "Durée"};
        sessionsTableModel = new DefaultTableModel(sessionColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable sessionsTable = new JTable(sessionsTableModel);
        sessionsTable.setRowHeight(25);
        sessionsTable.getTableHeader().setBackground(new Color(70, 130, 180));
        sessionsTable.getTableHeader().setForeground(Color.WHITE);
        sessionsTable.setSelectionBackground(new Color(173, 216, 230));

        JScrollPane scrollPane = new JScrollPane(sessionsTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Sessions Utilisateurs Actives"));

        panel.add(scrollPane, BorderLayout.CENTER);

        // Panel de contrôle pour les sessions
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBackground(new Color(240, 248, 255));

        JButton disconnectButton = new JButton("Déconnecter Session");
        disconnectButton.setBackground(new Color(220, 20, 60));
        disconnectButton.setForeground(Color.WHITE);
        disconnectButton.addActionListener(e -> {
            int selectedRow = sessionsTable.getSelectedRow();
            if (selectedRow >= 0) {
                String username = (String) sessionsTableModel.getValueAt(selectedRow, 0);
                disconnectUser(username);
            } else {
                JOptionPane.showMessageDialog(mainFrame, "Veuillez sélectionner une session à déconnecter");
            }
        });

        controlPanel.add(disconnectButton);
        panel.add(controlPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createClientsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 248, 255));

        String[] clientColumns = {"Adresse IP", "Port", "Heure de connexion", "Durée", "État"};
        clientsTableModel = new DefaultTableModel(clientColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable clientsTable = new JTable(clientsTableModel);
        clientsTable.setRowHeight(25);
        clientsTable.getTableHeader().setBackground(new Color(70, 130, 180));
        clientsTable.getTableHeader().setForeground(Color.WHITE);
        clientsTable.setSelectionBackground(new Color(173, 216, 230));

        JScrollPane scrollPane = new JScrollPane(clientsTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Clients TCP Connectés"));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 248, 255));

        logArea = new JTextArea(20, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(248, 248, 248));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Logs du Serveur"));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(scrollPane, BorderLayout.CENTER);

        // Panel de contrôle pour les logs
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBackground(new Color(240, 248, 255));

        JButton clearLogsButton = new JButton("Effacer Logs");
        clearLogsButton.setBackground(new Color(220, 20, 60));
        clearLogsButton.setForeground(Color.WHITE);
        clearLogsButton.addActionListener(e -> logArea.setText(""));

        controlPanel.add(clearLogsButton);
        panel.add(controlPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout());
        bottomPanel.setBackground(new Color(240, 248, 255));
        bottomPanel.setBorder(BorderFactory.createEtchedBorder());

        startStopButton = new JButton("Démarrer Serveur");
        startStopButton.setBackground(new Color(60, 179, 113));
        startStopButton.setForeground(Color.WHITE);
        startStopButton.setPreferredSize(new Dimension(150, 35));
        startStopButton.setFont(new Font("Arial", Font.BOLD, 12));

        startStopButton.addActionListener(e -> {
            if (!running) {
                startServer();
            } else {
                stopServer();
            }
        });

        JButton statsButton = new JButton("Statistiques");
        statsButton.setBackground(new Color(70, 130, 180));
        statsButton.setForeground(Color.WHITE);
        statsButton.setPreferredSize(new Dimension(120, 35));
        statsButton.addActionListener(e -> showStatistics());

        bottomPanel.add(startStopButton);
        bottomPanel.add(statsButton);

        return bottomPanel;
    }

    private void updateGUI() {
        SwingUtilities.invokeLater(() -> {
            // Mettre à jour les labels
            clientCountLabel.setText("Clients: " + connectedClients.size());
            activeSessionsLabel.setText("Sessions: " + activeSessions.size());

            // Mettre à jour le tableau des sessions
            updateSessionsTable();

            // Mettre à jour le tableau des clients
            updateClientsTable();
        });
    }

    private void updateSessionsTable() {
        sessionsTableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

        for (Map.Entry<String, SessionInfo> entry : activeSessions.entrySet()) {
            SessionInfo session = entry.getValue();
            long duration = (System.currentTimeMillis() - session.loginTime) / 1000;
            String durationStr = String.format("%02d:%02d:%02d",
                    duration / 3600, (duration % 3600) / 60, duration % 60);

            Object[] row = {
                    session.username,
                    session.clientIP,
                    sdf.format(new Date(session.loginTime)),
                    sdf.format(new Date(session.lastActivity)),
                    durationStr
            };
            sessionsTableModel.addRow(row);
        }
    }

    private void updateClientsTable() {
        clientsTableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

        for (ClientInfo client : connectedClients) {
            if (client.isActive) {
                long duration = (System.currentTimeMillis() - client.connectionTime) / 1000;
                String durationStr = String.format("%02d:%02d:%02d",
                        duration / 3600, (duration % 3600) / 60, duration % 60);

                Object[] row = {
                        client.clientIP,
                        client.clientPort,
                        sdf.format(new Date(client.connectionTime)),
                        durationStr,
                        "Connecté"
                };
                clientsTableModel.addRow(row);
            }
        }
    }

    private void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void disconnectUser(String username) {
        // Trouver et supprimer la session
        String sessionToRemove = null;
        for (Map.Entry<String, SessionInfo> entry : activeSessions.entrySet()) {
            if (entry.getValue().username.equals(username)) {
                sessionToRemove = entry.getKey();
                break;
            }
        }

        if (sessionToRemove != null) {
            activeSessions.remove(sessionToRemove);
            addLog("Session déconnectée pour l'utilisateur: " + username);
            JOptionPane.showMessageDialog(mainFrame,
                    "Utilisateur " + username + " déconnecté avec succès");
        }
    }

    private void showStatistics() {
        long uptime = (System.currentTimeMillis() - serverStartTime) / 1000;
        String uptimeStr = String.format("%02d:%02d:%02d",
                uptime / 3600, (uptime % 3600) / 60, uptime % 60);

        String stats = String.format(
                "Statistiques du Serveur\n\n" +
                        "Temps de fonctionnement: %s\n" +
                        "Connexions totales: %d\n" +
                        "Clients actuellement connectés: %d\n" +
                        "Sessions actives: %d\n" +
                        "Port d'écoute: %d\n" +
                        "Threads actifs: %d\n",
                uptimeStr, totalConnections, connectedClients.size(),
                activeSessions.size(), PORT, threadPool.toString().contains("pool") ? 10 : 0
        );

        JOptionPane.showMessageDialog(mainFrame, stats,
                "Statistiques", JOptionPane.INFORMATION_MESSAGE);
    }

    private void startServer() {
        try {
            SSLContext sslContext = createSSLContext();
            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
            serverSocket = (ServerSocket) ssf.createServerSocket(PORT);
            running = true;

            new Thread(() -> {
                try {
                    addLog("Serveur démarré sur le port " + PORT + " (HTTPS)");
                    while (running) {
                        Socket clientSocket = serverSocket.accept();
                        totalConnections++;
                        String clientIP = clientSocket.getInetAddress().getHostAddress();
                        int clientPort = clientSocket.getPort();
                        ClientInfo clientInfo = new ClientInfo(clientIP, clientPort);
                        connectedClients.add(clientInfo);
                        addLog("Nouvelle connexion sécurisée de " + clientIP + ":" + clientPort);
                        threadPool.submit(new ClientHandler(clientSocket, clientInfo));
                    }
                } catch (IOException e) {
                    if (running) {
                        addLog("Erreur serveur HTTPS: " + e.getMessage());
                    }
                }
            }).start();

            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("État: En marche (HTTPS)");
                statusLabel.setForeground(new Color(60, 179, 113));
                startStopButton.setText("Arrêter Serveur");
                startStopButton.setBackground(new Color(220, 20, 60));
            });

        } catch (Exception e) {
            addLog("Impossible de démarrer le serveur HTTPS: " + e.getMessage());
            JOptionPane.showMessageDialog(mainFrame,
                    "Impossible de démarrer le serveur HTTPS: " + e.getMessage());
        }
    }
    private void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            threadPool.shutdown();

            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("État: Arrêté");
                statusLabel.setForeground(Color.RED);
                startStopButton.setText("Démarrer Serveur");
                startStopButton.setBackground(new Color(60, 179, 113));
            });

            addLog("Serveur arrêté");

        } catch (IOException e) {
            addLog("Erreur lors de l'arrêt: " + e.getMessage());
        }
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Table des utilisateurs
            String createUsersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """;

            // Table des mots de passe stockés
            String createPasswordsTable = """
                CREATE TABLE IF NOT EXISTS stored_passwords (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    service_name TEXT NOT NULL,
                    username TEXT NOT NULL,
                    encrypted_password TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users (id)
                )
            """;

            conn.createStatement().execute(createUsersTable);
            conn.createStatement().execute(createPasswordsTable);

            addLog("Base de données initialisée");
        } catch (SQLException e) {
            addLog("Erreur d'initialisation de la base de données: " + e.getMessage());
        }
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private ClientInfo clientInfo;

        public ClientHandler(Socket socket, ClientInfo clientInfo) {
            this.clientSocket = socket;
            this.clientInfo = clientInfo;
        }
        private RPCResponse handleLogout(RPCRequest request) {
            String sessionId = (String) request.getParameters().get("sessionId");
            if (sessionId != null && activeSessions.containsKey(sessionId)) {
                SessionInfo session = activeSessions.remove(sessionId);
                addLog("Utilisateur déconnecté: " + session.username);
                return new RPCResponse(true, "Déconnexion réussie", null);
            }
            return new RPCResponse(false, "Session invalide", null);
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(clientSocket.getOutputStream());
                in = new ObjectInputStream(clientSocket.getInputStream());

                while (true) {
                    RPCRequest request = (RPCRequest) in.readObject();
                    addLog("Requête reçue: " + request.getMethod() + " de " + clientInfo.clientIP);

                    RPCResponse response = processRequest(request);
                    out.writeObject(response);
                    out.flush();
                }
            } catch (Exception e) {
                addLog("Client déconnecté: " + clientInfo.clientIP + " - " + e.getMessage());
            } finally {
                // Marquer le client comme inactif
                clientInfo.isActive = false;
                connectedClients.remove(clientInfo);

                try {
                    clientSocket.close();
                } catch (IOException e) {
                    addLog("Erreur fermeture socket: " + e.getMessage());
                }
            }
        }

        private RPCResponse processRequest(RPCRequest request) {
            try {
                switch (request.getMethod()) {
                    case "LOGIN":
                        return handleLogin(request);
                    case "REGISTER":
                        return handleRegister(request);
                    case "ADD_PASSWORD":
                        return handleAddPassword(request);
                    case "GET_PASSWORDS":
                        return handleGetPasswords(request);
                    case "DELETE_PASSWORD":
                        return handleDeletePassword(request);
                    case "UPDATE_PASSWORD":
                        return handleUpdatePassword(request);
                    case "DECRYPT_PASSWORD":
                        return handleDecryptPassword(request);
                    case "LOGOUT":
                        return handleLogout(request);
                    default:
                        return new RPCResponse(false, "Méthode inconnue", null);
                }
            } catch (Exception e) {
                addLog("Erreur traitement requête: " + e.getMessage());
                return new RPCResponse(false, "Erreur serveur: " + e.getMessage(), null);
            }
        }

        private RPCResponse handleLogin(RPCRequest request) {
            String username = (String) request.getParameters().get("username");
            String password = (String) request.getParameters().get("password");

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                String sql = "SELECT id, password_hash FROM users WHERE username = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String inputHash = hashPassword(password);

                    if (storedHash.equals(inputHash)) {
                        String sessionId = UUID.randomUUID().toString();
                        SessionInfo sessionInfo = new SessionInfo(username, clientInfo.clientIP);
                        activeSessions.put(sessionId, sessionInfo);

                        Map<String, Object> result = new HashMap<>();
                        result.put("sessionId", sessionId);
                        result.put("userId", rs.getInt("id"));

                        addLog("Connexion réussie pour: " + username + " depuis " + clientInfo.clientIP);
                        return new RPCResponse(true, "Connexion réussie", result);
                    }
                }

                addLog("Tentative de connexion échouée pour: " + username + " depuis " + clientInfo.clientIP);
                return new RPCResponse(false, "Nom d'utilisateur ou mot de passe incorrect", null);
            } catch (SQLException e) {
                addLog("Erreur base de données lors de la connexion: " + e.getMessage());
                return new RPCResponse(false, "Erreur de base de données", null);
            }
        }

        private RPCResponse handleRegister(RPCRequest request) {
            String username = (String) request.getParameters().get("username");
            String password = (String) request.getParameters().get("password");

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                // Vérifier si l'utilisateur existe déjà
                String checkSql = "SELECT COUNT(*) FROM users WHERE username = ?";
                PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                checkStmt.setString(1, username);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {
                    addLog("Tentative d'inscription avec nom existant: " + username);
                    return new RPCResponse(false, "Nom d'utilisateur déjà utilisé", null);
                }

                // Créer le nouvel utilisateur
                String insertSql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
                PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                insertStmt.setString(1, username);
                insertStmt.setString(2, hashPassword(password));
                insertStmt.executeUpdate();

                addLog("Nouvel utilisateur inscrit: " + username);
                return new RPCResponse(true, "Inscription réussie", null);
            } catch (SQLException e) {
                addLog("Erreur lors de l'inscription: " + e.getMessage());
                return new RPCResponse(false, "Erreur lors de l'inscription", null);
            }
        }

        private RPCResponse handleAddPassword(RPCRequest request) {
            String sessionId = (String) request.getParameters().get("sessionId");
            if (!activeSessions.containsKey(sessionId)) {
                return new RPCResponse(false, "Session invalide", null);
            }

            SessionInfo session = activeSessions.get(sessionId);
            session.updateActivity();

            String username = session.username;
            String serviceName = (String) request.getParameters().get("serviceName");
            String serviceUsername = (String) request.getParameters().get("serviceUsername");
            String password = (String) request.getParameters().get("password");

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                // Obtenir l'ID utilisateur
                String getUserSql = "SELECT id FROM users WHERE username = ?";
                PreparedStatement getUserStmt = conn.prepareStatement(getUserSql);
                getUserStmt.setString(1, username);
                ResultSet rs = getUserStmt.executeQuery();

                if (rs.next()) {
                    int userId = rs.getInt("id");
                    String encryptedPassword = encryptPassword(password, username);

                    String insertSql = "INSERT INTO stored_passwords (user_id, service_name, username, encrypted_password) VALUES (?, ?, ?, ?)";
                    PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                    insertStmt.setInt(1, userId);
                    insertStmt.setString(2, serviceName);
                    insertStmt.setString(3, serviceUsername);
                    insertStmt.setString(4, encryptedPassword);
                    insertStmt.executeUpdate();

                    addLog("Mot de passe ajouté pour " + username + " (service: " + serviceName + ")");
                    return new RPCResponse(true, "Mot de passe ajouté avec succès", null);
                }

                return new RPCResponse(false, "Utilisateur non trouvé", null);
            } catch (SQLException e) {
                addLog("Erreur ajout mot de passe: " + e.getMessage());
                return new RPCResponse(false, "Erreur lors de l'ajout", null);
            }
        }

        private RPCResponse handleGetPasswords(RPCRequest request) {
            String sessionId = (String) request.getParameters().get("sessionId");
            String inputPassword = (String) request.getParameters().get("password");

            if (!activeSessions.containsKey(sessionId)) {
                return new RPCResponse(false, "Session invalide", null);
            }

            SessionInfo session = activeSessions.get(sessionId);
            session.updateActivity();
            String username = session.username;

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                // Vérifier le mot de passe
                String checkPassSql = "SELECT password_hash FROM users WHERE username = ?";
                PreparedStatement checkPassStmt = conn.prepareStatement(checkPassSql);
                checkPassStmt.setString(1, username);
                ResultSet passRs = checkPassStmt.executeQuery();

                if (!passRs.next() || !passRs.getString("password_hash").equals(hashPassword(inputPassword))) {
                    addLog("Tentative d'accès non autorisée aux mots de passe pour: " + username);
                    return new RPCResponse(false, "Mot de passe incorrect", null);
                }

                // Récupérer les mots de passe
                String sql = """
                    SELECT sp.id, sp.service_name, sp.username, sp.encrypted_password 
                    FROM stored_passwords sp 
                    JOIN users u ON sp.user_id = u.id 
                    WHERE u.username = ?
                """;
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                List<Map<String, Object>> passwords = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> passwordEntry = new HashMap<>();
                    passwordEntry.put("id", rs.getInt("id"));
                    passwordEntry.put("serviceName", rs.getString("service_name"));
                    passwordEntry.put("username", rs.getString("username"));
                    passwordEntry.put("encryptedPassword", rs.getString("encrypted_password"));
                    passwords.add(passwordEntry);
                }

                addLog("Mots de passe récupérés pour: " + username + " (" + passwords.size() + " entrées)");
                return new RPCResponse(true, "Mots de passe récupérés", passwords);
            } catch (SQLException e) {
                addLog("Erreur récupération mots de passe: " + e.getMessage());
                return new RPCResponse(false, "Erreur lors de la récupération", null);
            }
        }

        private RPCResponse handleDecryptPassword(RPCRequest request) {
            String sessionId = (String) request.getParameters().get("sessionId");
            String encryptedPassword = (String) request.getParameters().get("encryptedPassword");

            if (!activeSessions.containsKey(sessionId)) {
                return new RPCResponse(false, "Session invalide", null);
            }

            SessionInfo session = activeSessions.get(sessionId);
            session.updateActivity();
            // Déchiffrement du mot de passe
            String decryptedPassword = decryptPassword(encryptedPassword, session.username);

            return new RPCResponse(true, "Mot de passe déchiffré", decryptedPassword);
        }

        private RPCResponse handleDeletePassword(RPCRequest request) {
            String sessionId = (String) request.getParameters().get("sessionId");
            int passwordId = (Integer) request.getParameters().get("passwordId");

            if (!activeSessions.containsKey(sessionId)) {
                return new RPCResponse(false, "Session invalide", null);
            }

            SessionInfo session = activeSessions.get(sessionId);
            session.updateActivity();
            String username = session.username;

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                String sql = "DELETE FROM stored_passwords WHERE id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, passwordId);
                stmt.setString(2, username);
                int rowsAffected = stmt.executeUpdate();

                if (rowsAffected > 0) {
                    addLog("Mot de passe supprimé pour " + username + " (ID: " + passwordId + ")");
                    return new RPCResponse(true, "Mot de passe supprimé", null);
                } else {
                    return new RPCResponse(false, "Mot de passe non trouvé ou n'appartient pas à l'utilisateur", null);
                }
            } catch (SQLException e) {
                addLog("Erreur suppression mot de passe: " + e.getMessage());
                return new RPCResponse(false, "Erreur lors de la suppression", null);
            }
        }

        private RPCResponse handleUpdatePassword(RPCRequest request) {
            String sessionId = (String) request.getParameters().get("sessionId");
            int passwordId = (Integer) request.getParameters().get("passwordId");
            String serviceName = (String) request.getParameters().get("serviceName");
            String serviceUsername = (String) request.getParameters().get("serviceUsername");
            String newPassword = (String) request.getParameters().get("password");

            if (!activeSessions.containsKey(sessionId)) {
                return new RPCResponse(false, "Session invalide", null);
            }

            SessionInfo session = activeSessions.get(sessionId);
            session.updateActivity();
            String username = session.username;
            String encryptedPassword = encryptPassword(newPassword, username);

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                String sql = "UPDATE stored_passwords SET service_name = ?, username = ?, encrypted_password = ? WHERE id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, serviceName);
                stmt.setString(2, serviceUsername);
                stmt.setString(3, encryptedPassword);
                stmt.setInt(4, passwordId);
                stmt.setString(5, username);
                int rowsAffected = stmt.executeUpdate();

                if (rowsAffected > 0) {
                    addLog("Mot de passe mis à jour pour " + username + " (ID: " + passwordId + ")");
                    return new RPCResponse(true, "Mot de passe mis à jour", null);
                } else {
                    return new RPCResponse(false, "Mot de passe non trouvé ou n'appartient pas à l'utilisateur", null);
                }
            } catch (SQLException e) {
                addLog("Erreur mise à jour mot de passe: " + e.getMessage());
                return new RPCResponse(false, "Erreur lors de la mise à jour", null);
            }
        }

        private String encryptPassword(String password, String username) {
            try {
                String key = username.length() >= 16 ? username.substring(0, 16) : String.format("%-16s", username).replace(' ', '0');
                SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);

                byte[] encryptedBytes = cipher.doFinal(password.getBytes());
                return Base64.getEncoder().encodeToString(encryptedBytes);
            } catch (Exception e) {
                throw new RuntimeException("Erreur de chiffrement", e);
            }
        }

        private String decryptPassword(String encryptedPassword, String username) {
            try {
                String key = username.length() >= 16 ? username.substring(0, 16) : String.format("%-16s", username).replace(' ', '0');
                SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, secretKey);

                byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
                return new String(decryptedBytes);
            } catch (Exception e) {
                throw new RuntimeException("Erreur de déchiffrement", e);
            }
        }

        private String hashPassword(String password) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hashedBytes = md.digest(password.getBytes());
                return Base64.getEncoder().encodeToString(hashedBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Erreur de hachage", e);
            }
        }
    }

    public static void main(String[] args) {
        try {
            PasswordManagerServer server = new PasswordManagerServer();
            server.startServer();
        } catch (IOException e) {
            System.err.println("Erreur lors du démarrage du serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }
}