import javax.net.ssl.*;
import java.security.KeyStore;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.Timer;

public class PasswordManagerClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String sessionId;
    private JFrame mainFrame;

    public PasswordManagerClient() {
        try {
            initLookAndFeel();
            connectToServer();
            showLoginWindow();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Impossible de se connecter au serveur: " + e.getMessage());
            System.exit(1);
        }
    }

    private void initLookAndFeel() throws Exception {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        }
    }

    private void connectToServer() throws IOException {
        try {
            // Charger le truststore
            System.setProperty("javax.net.ssl.trustStore", "clienttruststore.jks");
            System.setProperty("javax.net.ssl.trustStorePassword", "changeit");

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null); // Utilise le truststore système

            SSLSocketFactory sf = sslContext.getSocketFactory();
            socket = (SSLSocket) sf.createSocket(SERVER_HOST, SERVER_PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

        } catch (Exception e) {
            throw new IOException("Échec de connexion SSL: " + e.getMessage());
        }
    }
    private RPCResponse sendRequest(RPCRequest request) throws Exception {
        out.writeObject(request);
        out.flush();
        return (RPCResponse) in.readObject();
    }

    private void showLoginWindow() {
        JFrame loginFrame = new JFrame("Gestionnaire de Mots de Passe - Connexion");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(400, 300);
        loginFrame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(240, 248, 255));
        panel.setOpaque(true);
        GridBagConstraints gbc = new GridBagConstraints();

        // Titre
        JLabel titleLabel = new JLabel("Gestionnaire de Mots de Passe");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(new Color(70, 130, 180));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.insets = new Insets(20, 10, 20, 10);
        panel.add(titleLabel, gbc);

        // Nom d'utilisateur
        gbc.gridwidth = 1; gbc.insets = new Insets(5, 10, 5, 5);
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Nom d'utilisateur:"), gbc);

        JTextField usernameField = new JTextField(15);
        gbc.gridx = 1;
        panel.add(usernameField, gbc);

        // Mot de passe
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Mot de passe:"), gbc);

        JPasswordField passwordField = new JPasswordField(15);
        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        // Boutons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setOpaque(true);

        JButton loginButton = createStyledButton("Se connecter", new Color(70, 130, 180), Color.WHITE);
        JButton registerButton = createStyledButton("S'inscrire", new Color(60, 179, 113), Color.BLACK);

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.insets = new Insets(20, 10, 10, 10);
        panel.add(buttonPanel, gbc);

        // Actions des boutons
        loginButton.addActionListener(e -> handleLogin(loginFrame, usernameField, passwordField));
        registerButton.addActionListener(e -> handleRegister(loginFrame, usernameField, passwordField));

        // Enter pour se connecter
        KeyListener enterListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    loginButton.doClick();
                }
            }
        };
        usernameField.addKeyListener(enterListener);
        passwordField.addKeyListener(enterListener);

        loginFrame.add(panel);
        loginFrame.setVisible(true);
    }

    private JButton createStyledButton(String text, Color bgColor, Color fgColor) {
        JButton button = new JButton(text);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setBackground(bgColor);
        button.setForeground(fgColor);
        button.setPreferredSize(new Dimension(120, 30));
        button.setFocusPainted(false);
        return button;
    }

    private void handleLogin(JFrame loginFrame, JTextField usernameField, JPasswordField passwordField) {
        try {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(loginFrame, "Veuillez remplir tous les champs");
                return;
            }

            RPCRequest request = new RPCRequest("LOGIN");
            request.addParameter("username", username);
            request.addParameter("password", password);

            RPCResponse response = sendRequest(request);

            if (response.isSuccess()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.getData();
                sessionId = (String) result.get("sessionId");

                loginFrame.dispose();
                showMainWindow();
            } else {
                JOptionPane.showMessageDialog(loginFrame, response.getMessage());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(loginFrame, "Erreur de connexion: " + ex.getMessage());
        }
    }

    private void handleRegister(JFrame loginFrame, JTextField usernameField, JPasswordField passwordField) {
        try {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(loginFrame, "Veuillez remplir tous les champs");
                return;
            }

            if (password.length() < 6) {
                JOptionPane.showMessageDialog(loginFrame, "Le mot de passe doit contenir au moins 6 caractères");
                return;
            }

            RPCRequest request = new RPCRequest("REGISTER");
            request.addParameter("username", username);
            request.addParameter("password", password);

            RPCResponse response = sendRequest(request);

            if (response.isSuccess()) {
                JOptionPane.showMessageDialog(loginFrame, "Inscription réussie! Vous pouvez maintenant vous connecter.");
                usernameField.setText("");
                passwordField.setText("");
            } else {
                JOptionPane.showMessageDialog(loginFrame, response.getMessage());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(loginFrame, "Erreur d'inscription: " + ex.getMessage());
        }
    }

    private void showMainWindow() {
        mainFrame = new JFrame("Gestionnaire de Mots de Passe");
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logout();
            }
        });
        mainFrame.setSize(500, 400);
        mainFrame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 248, 255));
        panel.setOpaque(true);

        // Titre
        JLabel titleLabel = new JLabel("Menu Principal", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(new Color(70, 130, 180));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Boutons principaux
        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 10, 20));
        buttonPanel.setOpaque(true);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        JButton addPasswordButton = createMainButton("🔐 Ajouter un Mot de Passe", new Color(70, 130, 180));
        JButton viewPasswordsButton = createMainButton("📋 Afficher Mes Comptes", new Color(60, 179, 113));
        JButton logoutButton = createMainButton("🚪 Se Déconnecter", new Color(220, 20, 60));

        buttonPanel.add(addPasswordButton);
        buttonPanel.add(viewPasswordsButton);
        buttonPanel.add(logoutButton);

        panel.add(buttonPanel, BorderLayout.CENTER);

        // Actions des boutons
        addPasswordButton.addActionListener(e -> showAddPasswordWindow());
        viewPasswordsButton.addActionListener(e -> showPasswordsWindow());
        logoutButton.addActionListener(e -> logout());

        mainFrame.add(panel);
        mainFrame.setVisible(true);
    }

    private JButton createMainButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFont(new Font("Arial", Font.BOLD, 16));
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setPreferredSize(new Dimension(300, 60));
        button.setFocusPainted(false);
        return button;
    }

    private void showAddPasswordWindow() {
        JDialog dialog = new JDialog(mainFrame, "Ajouter un Mot de Passe", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(240, 248, 255));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setOpaque(true);

        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        formPanel.setBackground(new Color(240, 248, 255));
        formPanel.setOpaque(true);

        // Service
        formPanel.add(new JLabel("Service (Gmail, Facebook, etc.):"));
        JTextField serviceField = new JTextField();
        formPanel.add(serviceField);

        // Nom d'utilisateur
        formPanel.add(new JLabel("Nom d'utilisateur:"));
        JTextField usernameField = new JTextField();
        formPanel.add(usernameField);

        // Mot de passe
        formPanel.add(new JLabel("Mot de passe:"));
        JPasswordField passwordField = new JPasswordField();
        formPanel.add(passwordField);

        panel.add(formPanel, BorderLayout.CENTER);

        // Boutons
        JButton saveButton = createStyledButton("Sauvegarder", new Color(70, 130, 180), Color.WHITE);
        JButton cancelButton = createStyledButton("Annuler", new Color(220, 20, 60), Color.WHITE);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setOpaque(true);
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                serviceField.requestFocus();
            }
        });

        saveButton.addActionListener(e -> {
            try {
                String service = serviceField.getText().trim();
                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword());

                if (service.isEmpty() || username.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Veuillez remplir tous les champs");
                    return;
                }

                RPCRequest request = new RPCRequest("ADD_PASSWORD");
                request.addParameter("sessionId", sessionId);
                request.addParameter("serviceName", service);
                request.addParameter("serviceUsername", username);
                request.addParameter("password", password);

                RPCResponse response = sendRequest(request);

                if (response.isSuccess()) {
                    JOptionPane.showMessageDialog(dialog, "Mot de passe ajouté avec succès!");
                    dialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(dialog, response.getMessage());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Erreur: " + ex.getMessage());
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void showPasswordsWindow() {
        JPasswordField passwordField = new JPasswordField();
        int option = JOptionPane.showConfirmDialog(mainFrame,
                passwordField,
                "Entrez votre mot de passe pour accéder à vos comptes:",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (option != JOptionPane.OK_OPTION) {
            return;
        }

        String password = new String(passwordField.getPassword());
        if (password.isEmpty()) {
            return;
        }

        try {
            RPCRequest request = new RPCRequest("GET_PASSWORDS");
            request.addParameter("sessionId", sessionId);
            request.addParameter("password", password);

            RPCResponse response = sendRequest(request);

            if (!response.isSuccess()) {
                JOptionPane.showMessageDialog(mainFrame, response.getMessage());
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> passwords = (List<Map<String, Object>>) response.getData();

            if (passwords.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "Aucun mot de passe enregistré.");
                return;
            }

            JDialog dialog = new JDialog(mainFrame, "Mes Comptes", true);
            dialog.setSize(800, 500);
            dialog.setLocationRelativeTo(mainFrame);
            dialog.setLayout(new BorderLayout());

            // Panel de recherche
            JPanel searchPanel = new JPanel(new BorderLayout());
            searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            searchPanel.setBackground(new Color(240, 248, 255));

            JLabel searchLabel = new JLabel("Rechercher:");
            JTextField searchField = new JTextField();
            searchField.setPreferredSize(new Dimension(200, 25));

            searchPanel.add(searchLabel, BorderLayout.WEST);
            searchPanel.add(searchField, BorderLayout.CENTER);

            dialog.add(searchPanel, BorderLayout.NORTH);

            String[] columnNames = {"Service", "Nom d'utilisateur", "Mot de passe", "Actions"};
            DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 3;
                }
            };

            for (Map<String, Object> passwordEntry : passwords) {
                Object[] row = {
                        passwordEntry.get("serviceName"),
                        passwordEntry.get("username"),
                        "••••••••",
                        "Actions"
                };
                model.addRow(row);
            }

            JTable table = new JTable(model);
            table.setRowHeight(40);
            table.getColumnModel().getColumn(0).setPreferredWidth(150);
            table.getColumnModel().getColumn(1).setPreferredWidth(150);
            table.getColumnModel().getColumn(2).setPreferredWidth(100);
            table.getColumnModel().getColumn(3).setPreferredWidth(200);

            table.getColumn("Actions").setCellRenderer(new ButtonRenderer());
            table.getColumn("Actions").setCellEditor(new ButtonEditor(table, passwords, dialog));

            JScrollPane scrollPane = new JScrollPane(table);
            dialog.add(scrollPane, BorderLayout.CENTER);

            // Fonctionnalité de recherche
            searchField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    String searchText = searchField.getText().toLowerCase();
                    TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
                    table.setRowSorter(sorter);

                    if (searchText.trim().length() == 0) {
                        sorter.setRowFilter(null);
                    } else {
                        sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText));
                    }
                }
            });

            JButton closeButton = createStyledButton("Fermer", new Color(70, 130, 180), Color.WHITE);
            closeButton.addActionListener(e -> dialog.dispose());

            JPanel bottomPanel = new JPanel(new FlowLayout());
            bottomPanel.setOpaque(true);
            bottomPanel.add(closeButton);
            dialog.add(bottomPanel, BorderLayout.SOUTH);

            dialog.setVisible(true);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame, "Erreur: " + ex.getMessage());
        }
    }
    private class ButtonRenderer extends JPanel implements TableCellRenderer {
        private JButton viewButton, editButton, deleteButton;

        public ButtonRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 2, 2));
            setOpaque(true);

            viewButton = createActionButton("Voir", new Color(70, 130, 180));
            editButton = createActionButton("Modifier", new Color(60, 179, 113));
            deleteButton = createActionButton("Supprimer", new Color(220, 20, 60));

            add(viewButton);
            add(editButton);
            add(deleteButton);
        }

        private JButton createActionButton(String text, Color bgColor) {
            JButton button = new JButton(text);
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setBorderPainted(true);
            button.setBackground(bgColor);
            button.setForeground(Color.WHITE);
            button.setPreferredSize(new Dimension(60, 25));
            button.setFont(new Font("Arial", Font.PLAIN, 9));
            button.setFocusPainted(false);
            return button;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(table.getBackground());
            }
            return this;
        }
    }

    private class ButtonEditor extends DefaultCellEditor {
        private JPanel panel;
        private JButton viewButton, editButton, deleteButton;
        private JTable table;
        private List<Map<String, Object>> passwords;
        private JDialog parentDialog;
        private int currentRow = -1;

        public ButtonEditor(JTable table, List<Map<String, Object>> passwords, JDialog parentDialog) {
            super(new JTextField());
            this.table = table;
            this.passwords = passwords;
            this.parentDialog = parentDialog;

            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
            panel.setOpaque(true);

            viewButton = createActionButton("Voir", new Color(70, 130, 180));
            editButton = createActionButton("Modifier", new Color(60, 179, 113));
            deleteButton = createActionButton("Supprimer", new Color(220, 20, 60));

            viewButton.addActionListener(e -> {
                stopCellEditing();
                viewPassword();
            });
            editButton.addActionListener(e -> {
                stopCellEditing();
                editPassword();
            });
            deleteButton.addActionListener(e -> {
                stopCellEditing();
                deletePassword();
            });

            panel.add(viewButton);
            panel.add(editButton);
            panel.add(deleteButton);
        }

        private JButton createActionButton(String text, Color bgColor) {
            JButton button = new JButton(text);
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setBorderPainted(true);
            button.setBackground(bgColor);
            button.setForeground(Color.WHITE);
            button.setPreferredSize(new Dimension(60, 25));
            button.setFont(new Font("Arial", Font.PLAIN, 9));
            button.setFocusPainted(false);
            return button;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            currentRow = row;
            return panel;
        }

        private void viewPassword() {
            if (currentRow >= 0 && currentRow < passwords.size()) {
                Map<String, Object> passwordEntry = passwords.get(currentRow);
                String encryptedPassword = (String) passwordEntry.get("encryptedPassword");

                try {
                    RPCRequest request = new RPCRequest("DECRYPT_PASSWORD");
                    request.addParameter("sessionId", sessionId);
                    request.addParameter("encryptedPassword", encryptedPassword);

                    RPCResponse response = sendRequest(request);

                    if (response.isSuccess()) {
                        String decryptedPassword = (String) response.getData();

                        JDialog passwordDialog = new JDialog(parentDialog, "Mot de passe", true);
                        passwordDialog.setSize(350, 200);
                        passwordDialog.setLocationRelativeTo(parentDialog);

                        JPanel dialogPanel = new JPanel(new BorderLayout(10, 10));
                        dialogPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                        dialogPanel.setOpaque(true);

                        JLabel serviceLabel = new JLabel("Service: " + passwordEntry.get("serviceName"));
                        serviceLabel.setFont(new Font("Arial", Font.BOLD, 12));

                        JLabel usernameLabel = new JLabel("Utilisateur: " + passwordEntry.get("username"));
                        usernameLabel.setFont(new Font("Arial", Font.PLAIN, 12));

                        JLabel passwordLabel = new JLabel("Mot de passe: " + decryptedPassword);
                        passwordLabel.setFont(new Font("Arial", Font.BOLD, 14));
                        passwordLabel.setForeground(new Color(70, 130, 180));

                        JLabel timerLabel = new JLabel("Se ferme dans 10 secondes", SwingConstants.CENTER);
                        timerLabel.setForeground(Color.RED);
                        timerLabel.setFont(new Font("Arial", Font.ITALIC, 10));

                        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 5, 5));
                        infoPanel.setOpaque(true);
                        infoPanel.add(serviceLabel);
                        infoPanel.add(usernameLabel);
                        infoPanel.add(passwordLabel);

                        dialogPanel.add(infoPanel, BorderLayout.CENTER);
                        dialogPanel.add(timerLabel, BorderLayout.SOUTH);

                        passwordDialog.add(dialogPanel);

                        Timer timer = new Timer();
                        timer.schedule(new TimerTask() {
                            int seconds = 10;

                            @Override
                            public void run() {
                                if (seconds <= 0) {
                                    SwingUtilities.invokeLater(() -> {
                                        passwordDialog.dispose();
                                        timer.cancel();
                                    });
                                } else {
                                    SwingUtilities.invokeLater(() ->
                                            timerLabel.setText("Se ferme dans " + seconds + " secondes"));
                                    seconds--;
                                }
                            }
                        }, 0, 1000);

                        passwordDialog.setVisible(true);
                    } else {
                        JOptionPane.showMessageDialog(parentDialog, response.getMessage());
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parentDialog, "Erreur: " + ex.getMessage());
                }
            }
        }

        private void editPassword() {
            if (currentRow >= 0 && currentRow < passwords.size()) {
                Map<String, Object> passwordEntry = passwords.get(currentRow);
                showEditPasswordDialog(passwordEntry, currentRow);
            }
        }

        private void deletePassword() {
            if (currentRow >= 0 && currentRow < passwords.size()) {
                int result = JOptionPane.showConfirmDialog(parentDialog,
                        "Êtes-vous sûr de vouloir supprimer ce compte?",
                        "Confirmation",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (result == JOptionPane.YES_OPTION) {
                    Map<String, Object> passwordEntry = passwords.get(currentRow);
                    int passwordId = (Integer) passwordEntry.get("id");

                    try {
                        RPCRequest request = new RPCRequest("DELETE_PASSWORD");
                        request.addParameter("sessionId", sessionId);
                        request.addParameter("passwordId", passwordId);

                        RPCResponse response = sendRequest(request);

                        if (response.isSuccess()) {
                            JOptionPane.showMessageDialog(parentDialog, "Compte supprimé avec succès!");
                            parentDialog.dispose();
                            showPasswordsWindow();
                        } else {
                            JOptionPane.showMessageDialog(parentDialog, response.getMessage());
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(parentDialog, "Erreur: " + ex.getMessage());
                    }
                }
            }
        }
    }

    private void showEditPasswordDialog(Map<String, Object> passwordEntry, int row) {
        JDialog dialog = new JDialog(mainFrame, "Modifier le Mot de Passe", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(240, 248, 255));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setOpaque(true);

        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        formPanel.setBackground(new Color(240, 248, 255));
        formPanel.setOpaque(true);

        // Service
        formPanel.add(new JLabel("Service:"));
        JTextField serviceField = new JTextField();
        serviceField.setText((String) passwordEntry.get("serviceName"));
        formPanel.add(serviceField);

        // Nom d'utilisateur
        formPanel.add(new JLabel("Nom d'utilisateur:"));
        JTextField usernameField = new JTextField();
        usernameField.setText((String) passwordEntry.get("username"));
        formPanel.add(usernameField);

        // Nouveau mot de passe
        formPanel.add(new JLabel("Nouveau mot de passe:"));
        JPasswordField passwordField = new JPasswordField();
        formPanel.add(passwordField);

        panel.add(formPanel, BorderLayout.CENTER);

        // Boutons
        JButton saveButton = createStyledButton("Sauvegarder", new Color(70, 130, 180), Color.WHITE);
        JButton cancelButton = createStyledButton("Annuler", new Color(220, 20, 60), Color.WHITE);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setOpaque(true);
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                serviceField.requestFocus();
            }
        });

        saveButton.addActionListener(e -> {
            try {
                String service = serviceField.getText().trim();
                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword());

                if (service.isEmpty() || username.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Veuillez remplir tous les champs");
                    return;
                }

                int passwordId = (Integer) passwordEntry.get("id");

                RPCRequest request = new RPCRequest("UPDATE_PASSWORD");
                request.addParameter("sessionId", sessionId);
                request.addParameter("passwordId", passwordId);
                request.addParameter("serviceName", service);
                request.addParameter("serviceUsername", username);
                request.addParameter("password", password);

                RPCResponse response = sendRequest(request);

                if (response.isSuccess()) {
                    JOptionPane.showMessageDialog(dialog, "Mot de passe modifié avec succès!");
                    dialog.dispose();
                    showPasswordsWindow();
                } else {
                    JOptionPane.showMessageDialog(dialog, response.getMessage());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Erreur: " + ex.getMessage());
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void logout() {
        try {
            RPCRequest request = new RPCRequest("LOGOUT");
            request.addParameter("sessionId", sessionId);
            sendRequest(request);

            mainFrame.dispose();
            showLoginWindow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame, "Erreur lors de la déconnexion: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PasswordManagerClient();
        });
    }
}
