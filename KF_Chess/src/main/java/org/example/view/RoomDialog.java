package org.example.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Small modal dialog that lets the user either create a brand-new room
 * or join an existing one by typing its room name / id.
 *
 * This is intentionally its own class (rather than a method inside
 * LobbyWindow) because:
 *   1. A JDialog has its own lifecycle (modal, own event loop) that is
 *      conceptually separate from the main lobby JFrame.
 *   2. It can now be reused/tested/opened from anywhere without dragging
 *      the whole LobbyWindow along with it.
 *   3. It keeps LobbyWindow focused on "which screen am I showing"
 *      instead of also owning "how does the room popup work".
 */
public class RoomDialog extends JDialog {

    /** Callback contract the dialog needs from whoever opens it. */
    public interface RoomDialogListener {
        void onCreateRoom();
        void onJoinRoom(String roomId);
    }

    // Same palette as LobbyWindow so the popup doesn't look like a
    // different app bolted onto the main window.
    private static final Color BG            = Color.WHITE;
    private static final Color TEXT_MUTED     = new Color(110, 118, 129);
    private static final Color ACCENT_CREATE  = new Color(38, 166, 91);   // green: "makes something new"
    private static final Color ACCENT_JOIN    = new Color(52, 120, 220);  // blue: matches primary lobby button
    private static final Color ACCENT_CANCEL  = new Color(210, 213, 218); // neutral gray: least emphasis
    private static final Font  FONT_LABEL     = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font  FONT_FIELD     = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font  FONT_BUTTON    = new Font("Segoe UI", Font.BOLD, 12);

    public RoomDialog(JFrame owner, RoomDialogListener listener) {
        super(owner, "Room", true); // true = modal, matches the screenshot's behavior
        setResizable(false);
        setSize(360, 210);
        setLocationRelativeTo(owner);

        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(22, 26, 22, 26));

        JLabel label = new JLabel("Room name");
        label.setFont(FONT_LABEL);
        label.setForeground(TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField roomField = new JTextField();
        roomField.setFont(FONT_FIELD);
        roomField.setAlignmentX(Component.LEFT_ALIGNMENT);
        roomField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        roomField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 204, 209), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        JPanel buttonRow = new JPanel(new GridLayout(1, 3, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JButton btnCreate = flatButton("Create", ACCENT_CREATE, Color.WHITE);
        JButton btnJoin   = flatButton("Join", ACCENT_JOIN, Color.WHITE);
        JButton btnCancel = flatButton("Cancel", ACCENT_CANCEL, new Color(60, 60, 60));

        buttonRow.add(btnCreate);
        buttonRow.add(btnJoin);
        buttonRow.add(btnCancel);

        content.add(label);
        content.add(Box.createVerticalStrut(6));
        content.add(roomField);
        content.add(Box.createVerticalStrut(22));
        content.add(buttonRow);

        // --- wiring ---
        // Create doesn't need the text field: per the spec, the server
        // generates the room id and the client just displays it once
        // it comes back over the event bus.
        btnCreate.addActionListener(e -> {
            listener.onCreateRoom();
            dispose();
        });

        // Join does need a non-empty id typed by the user.
        btnJoin.addActionListener(e -> {
            String roomId = roomField.getText().trim();
            if (roomId.isEmpty()) {
                roomField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(214, 69, 69), 1, true),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
                roomField.requestFocusInWindow();
                return; // don't close the dialog on invalid input
            }
            listener.onJoinRoom(roomId);
            dispose();
        });

        btnCancel.addActionListener(e -> dispose());

        setContentPane(content);
    }

    /** Flat, filled button matching the modern look used across the lobby. */
    private JButton flatButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(FONT_BUTTON);
        button.setForeground(foreground);
        button.setBackground(background);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
}
