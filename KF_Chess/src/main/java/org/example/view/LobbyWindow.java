package org.example.view;

import org.example.bus.GameEventBus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LobbyWindow {

    private final JFrame frame;
    private final CardLayout cardLayout;
    private final JPanel mainPanel;

    // הגדרת המצבים כאופציות ברורות
    public enum LobbyState { LOGIN, LOBBY, SEARCHING }

    public interface LobbyEventListener {
        void onLoginRequested(String username, String password);
        void onFindMatchRequested();
        void onCancelMatchmakingRequested();
        void onJoinRoomRequested(String roomId);
    }

    public LobbyWindow(LobbyEventListener listener) {
        frame = new JFrame("Kung Fu Chess - Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 400);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // הוספת 3 המצבים ל-CardLayout
        mainPanel.add(createLoginPanel(listener), LobbyState.LOGIN.name());
        mainPanel.add(createLobbyPanel(listener), LobbyState.LOBBY.name());
        mainPanel.add(createSearchingPanel(listener), LobbyState.SEARCHING.name());

        frame.add(mainPanel);
        switchState(LobbyState.LOGIN);

        registerEventBusListeners();
    }

    public void show() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    public void close() {
        SwingUtilities.invokeLater(frame::dispose);
    }

    public void switchState(LobbyState state) {
        SwingUtilities.invokeLater(() -> cardLayout.show(mainPanel, state.name()));
    }

    // -----------------------------------------------------------------
    // 🎨 בניית המצבים (Panels)
    // -----------------------------------------------------------------

    private JPanel createLoginPanel(LobbyEventListener listener) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(30, 35, 30, 35));

        JLabel title = new JLabel("KF CHESS LOGIN");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField userField = new JTextField(15);
        JPasswordField passField = new JPasswordField(15);

        JButton btnLogin = new JButton("Login");
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogin.setMaximumSize(new Dimension(300, 40));

        panel.add(title);
        panel.add(Box.createVerticalStrut(25));
        panel.add(createFieldPanel("Username:", userField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createFieldPanel("Password:", passField));
        panel.add(Box.createVerticalStrut(25));
        panel.add(btnLogin);

        btnLogin.addActionListener(e -> {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            if (!user.isEmpty() && !pass.isEmpty()) {
                listener.onLoginRequested(user, pass);
            }
        });

        return panel;
    }

    private JPanel createLobbyPanel(LobbyEventListener listener) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(30, 35, 30, 35));

        JLabel title = new JLabel("SELECT GAME MODE");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnPlay = new JButton("⚔️ Find Ranked Match (Play)");
        btnPlay.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnPlay.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPlay.setMaximumSize(new Dimension(300, 45));

        JTextField roomField = new JTextField(15);
        JButton btnJoinRoom = new JButton("🚪 Join Custom Room");
        btnJoinRoom.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnJoinRoom.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnJoinRoom.setMaximumSize(new Dimension(300, 35));

        panel.add(title);
        panel.add(Box.createVerticalStrut(25));
        panel.add(btnPlay);
        panel.add(Box.createVerticalStrut(20));
        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldPanel("Private Room ID:", roomField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(btnJoinRoom);

        btnPlay.addActionListener(e -> {
            switchState(LobbyState.SEARCHING); // מעבר מיידי למסך החיפוש
            listener.onFindMatchRequested();
        });

        btnJoinRoom.addActionListener(e -> {
            String room = roomField.getText().trim();
            if (!room.isEmpty()) {
                listener.onJoinRoomRequested(room);
            }
        });

        return panel;
    }

    private JPanel createSearchingPanel(LobbyEventListener listener) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(40, 35, 40, 35));

        JLabel label = new JLabel("<html><center><h2>Searching for Opponent...</h2><p>(±100 ELO Rating)</p></center></html>", SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnCancel = new JButton("Cancel Search");
        btnCancel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnCancel.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnCancel.setMaximumSize(new Dimension(200, 40));

        panel.add(label);
        panel.add(Box.createVerticalStrut(30));
        panel.add(btnCancel);

        btnCancel.addActionListener(e -> {
            switchState(LobbyState.LOBBY); // החזרה למסך הלובי
            listener.onCancelMatchmakingRequested();
        });

        return panel;
    }

    private void registerEventBusListeners() {
        // אימות הצליח -> מעבר לממסך הלובי
        GameEventBus.getInstance().subscribe("LOGIN_SUCCESS", data -> {
            System.out.println("✅ Login successful! Switching to Lobby screen.");
            switchState(LobbyState.LOBBY); // 👈 השורה הזו מקפיצה את המסך עם כפתור ה-Play!
        });
        // אימות נכשל -> הצגת הודעה
        GameEventBus.getInstance().subscribe("LOGIN_REJECTED", data ->
                JOptionPane.showMessageDialog(frame, "Login Failed: " + data, "Error", JOptionPane.ERROR_MESSAGE)
        );

        GameEventBus.getInstance().subscribe("JOIN_ACCEPTED", data -> {
            System.out.println("⚔️ Joined room successfully! Closing launcher.");
            close();
        });

        GameEventBus.getInstance().subscribe("JOIN_REJECTED", data -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, "Failed: " + data, "Error", JOptionPane.ERROR_MESSAGE)
        ));

        GameEventBus.getInstance().subscribe("MATCH_FOUND", data -> {
            System.out.println("⚔️ Match found! Closing lobby...");
            close();
        });

        GameEventBus.getInstance().subscribe("GAME_STARTED", data -> {
            close();
        });    }

    private JPanel createFieldPanel(String labelText, JTextField field) {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setOpaque(false);
        p.add(new JLabel(labelText), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }
}