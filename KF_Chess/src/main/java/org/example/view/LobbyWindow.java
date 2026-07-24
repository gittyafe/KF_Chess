package org.example.view;

import lombok.extern.slf4j.Slf4j;
import org.example.bus.GameEventBus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
public class LobbyWindow {

    private final JFrame frame;
    private final CardLayout cardLayout;
    private final JPanel mainPanel;

    // 🟢 הפניה לדיאלוג החדר הפעיל כדי לנהל אותו באופן מרכזי ולמנוע הודעות כפולות
    private RoomDialog activeRoomDialog = null;

    public enum LobbyState { LOGIN, LOBBY, SEARCHING }

    public interface LobbyEventListener {
        void onLoginRequested(String username, String password);
        void onFindMatchRequested();
        void onCancelMatchmakingRequested();
        void onCreateRoomRequested(String roomId);
        void onJoinRoomRequested(String roomId);
    }

    // ---------------------------------------------------------------
    // 🎨 Shared visual language for every panel in this window.
    // Centralizing these means every screen automatically matches, and
    // a future palette change only happens in one place.
    // ---------------------------------------------------------------
    private static final Color BG_PANEL     = new Color(248, 249, 251);
    private static final Color TEXT_TITLE   = new Color(33, 37, 41);
    private static final Color TEXT_MUTED   = new Color(110, 118, 129);
    private static final Color ACCENT       = new Color(52, 120, 220);   // primary action (Play)
    private static final Color ACCENT_DARK  = new Color(33, 87, 171);
    private static final Color NEUTRAL      = new Color(233, 235, 238);  // secondary action (Room)

    private static final Font FONT_TITLE  = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_SUB    = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 14);

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
        panel.setBackground(BG_PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(30, 35, 30, 35));

        JLabel title = new JLabel("KF CHESS LOGIN");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_TITLE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField userField = new JTextField(10);
        JPasswordField passField = new JPasswordField(10);
        styleField(userField);
        styleField(passField);

        JButton btnLogin = primaryButton("Login");
        btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogin.setMaximumSize(new Dimension(300, 42));

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

    /**
     * Lobby screen exposes two choices, side by side:
     *   - "Find Ranked Match" (unchanged behavior, restyled)
     *   - "Room" which opens the RoomDialog (Create / Join / Cancel)
     */
    private JPanel createLobbyPanel(LobbyEventListener listener) {
        JPanel panel = new JPanel();
        panel.setBackground(BG_PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(35, 35, 35, 35));

        JLabel title = new JLabel("SELECT GAME MODE");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_TITLE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Choose how you want to play");
        subtitle.setFont(FONT_SUB);
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnPlay = primaryButton("Find Ranked Match");
        btnPlay.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPlay.setMaximumSize(new Dimension(300, 46));

        JButton btnRoom = secondaryButton("Room");
        btnRoom.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnRoom.setMaximumSize(new Dimension(300, 46));

        panel.add(title);
        panel.add(Box.createVerticalStrut(6));
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(30));
        panel.add(btnPlay);
        panel.add(Box.createVerticalStrut(14));
        panel.add(btnRoom);

        btnPlay.addActionListener(e -> {
            switchState(LobbyState.SEARCHING);
            listener.onFindMatchRequested();
        });

        // 🟢 שומרים את הדיאלוג במיקום המרכזי activeRoomDialog בעת הפתיחה
        btnRoom.addActionListener(e -> {
            activeRoomDialog = new RoomDialog(frame, new RoomDialog.RoomDialogListener() {
                @Override
                public void onCreateRoom(String roomId) {
                    listener.onCreateRoomRequested(roomId);
                }

                @Override
                public void onJoinRoom(String roomId) {
                    listener.onJoinRoomRequested(roomId);
                }
            });
            activeRoomDialog.setVisible(true);
        });

        return panel;
    }

    private JPanel createSearchingPanel(LobbyEventListener listener) {
        JPanel panel = new JPanel();
        panel.setBackground(BG_PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(45, 35, 45, 35));

        JLabel label = new JLabel(
                "<html><center><h2 style='color:#212529;'>Searching for Opponent...</h2>"
                        + "<p style='color:#6e7681;'>(±100 ELO Rating)</p></center></html>",
                SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnCancel = secondaryButton("Cancel Search");
        btnCancel.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnCancel.setMaximumSize(new Dimension(220, 42));

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
        // אימות הצליח -> מעבר למסך הלובי
        GameEventBus.getInstance().subscribe("LOGIN_SUCCESS", data -> {
            switchState(LobbyState.LOBBY);
        });

        GameEventBus.getInstance().subscribe("LOGIN_REJECTED", data -> {
            String reason = (data != null) ? data.toString() : "Unknown reason";
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "Login Failed: " + reason, "Error", JOptionPane.ERROR_MESSAGE)
            );
        });

        // 🟢 הצטרפות לחדר אושרה -> סוגרים את דיאלוג החדר וסוגרים את ה-Launcher
        GameEventBus.getInstance().subscribe("JOIN_ACCEPTED", data -> SwingUtilities.invokeLater(() -> {
            log.info("[CLIENT OUT] Join room accepted! Closing launcher.");
            if (activeRoomDialog != null) {
                activeRoomDialog.close();
                activeRoomDialog = null;
            }
            close();
        }));

        // 🟢 הצטרפות לחדר נדחתה -> מציגים הודעה יחידה בדיאלוג הקיים ומנקים את התיבה
        GameEventBus.getInstance().subscribe("JOIN_REJECTED", data -> SwingUtilities.invokeLater(() -> {
            if (activeRoomDialog != null && activeRoomDialog.isShowing()) {
                String reason = data != null ? data.toString() : "Failed to join: Room does not exist";
                activeRoomDialog.showError(reason);
            }
        }));

        // 🟢 החדר נוצר בהצלחה -> השרת אישר שהשם פנוי!
        GameEventBus.getInstance().subscribe("CREATE_ACCEPTED", data -> SwingUtilities.invokeLater(() -> {
            if (activeRoomDialog != null && activeRoomDialog.isShowing()) {
                String roomId = data != null ? data.toString() : "";
                activeRoomDialog.showCreatedSuccess(roomId);
            }
        }));

// 🔴 יצירת החדר נדחתה -> השם כבר תפוס בשרת!
        GameEventBus.getInstance().subscribe("CREATE_REJECTED", data -> SwingUtilities.invokeLater(() -> {
            if (activeRoomDialog != null && activeRoomDialog.isShowing()) {
                String reason = data != null ? data.toString() : "Room ID already exists";
                activeRoomDialog.showError("Cannot create room: " + reason);
            }
        }));

// ⚔️ היריב נכנס והמשחק מתחיל -> רק עכשיו סוגרים את ה-Launcher וממשיכים למסך המשחק!
        GameEventBus.getInstance().subscribe("GAME_STARTED", data -> SwingUtilities.invokeLater(() -> {
            if (activeRoomDialog != null) {
                activeRoomDialog.close();
                activeRoomDialog = null;
            }
            close();
        }));

        // התאמת Matchmaking אושרה -> סגירת ה-Launcher
        GameEventBus.getInstance().subscribe("MATCH_FOUND", data -> SwingUtilities.invokeLater(() -> {
            log.info("[CLIENT OUT] Match found! Closing lobby...");
            close();
        }));

        GameEventBus.getInstance().subscribe("MATCHMAKING_TIMEOUT", data -> SwingUtilities.invokeLater(() -> {
            String reason = data != null ? data.toString() : "Matchmaking timed out";
            JOptionPane.showMessageDialog(frame, "Matchmaking failed: " + reason, "Error", JOptionPane.ERROR_MESSAGE);
            switchState(LobbyState.LOBBY);
        }));

    }

    private JPanel createFieldPanel(String labelText, JTextField field) {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(FONT_SUB);
        label.setForeground(TEXT_MUTED);
        p.add(label, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private void styleField(JTextField field) {
        field.setFont(FONT_SUB);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 204, 209), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
    }

    /** Filled, high-emphasis button for the single "main" action on a screen. */
    private JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(FONT_BUTTON);
        button.setForeground(Color.WHITE);
        button.setBackground(ACCENT);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getModel().addChangeListener(e -> {
            ButtonModel m = button.getModel();
            button.setBackground(m.isPressed() ? ACCENT_DARK.darker() : m.isRollover() ? ACCENT_DARK : ACCENT);
        });
        return button;
    }

    /** Lighter, low-emphasis button for secondary actions (Room, Cancel). */
    private JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(FONT_BUTTON);
        button.setForeground(TEXT_TITLE);
        button.setBackground(NEUTRAL);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getModel().addChangeListener(e -> {
            ButtonModel m = button.getModel();
            button.setBackground(m.isPressed() ? NEUTRAL.darker() : m.isRollover() ? NEUTRAL.darker() : NEUTRAL);
        });
        return button;
    }
}