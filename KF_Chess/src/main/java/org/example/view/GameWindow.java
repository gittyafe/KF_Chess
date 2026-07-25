package org.example.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.example.bus.GameEventBus;
import org.example.controllers.NetworkController;
import org.example.models.Role;

public class GameWindow {
    private final JFrame frame;
    private final JLabel imageLabel;
    private final JLabel statusBoxLabel; // 📦 הקופסה הקבועה להצגת התפקיד
    private final BoardGeometry geometry;

    // 🟢 1. רכיבי UI וטיימר לספירה לאחור של התנתקות
    private final JLabel disconnectLabel;
    private Timer uiCountdownTimer;

    private record BoardOffset(int x, int y) {}
    private volatile BoardOffset boardOffset = new BoardOffset(0, 0);

    public GameWindow(String title, int initialWidth, int initialHeight, BoardGeometry geometry) {
        this.geometry = geometry;

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);
        frame.setLayout(new BorderLayout());

        // 🟢 יצירת הקופסה הקבועה בראש החלון
        statusBoxLabel = new JLabel("Role: " + Role.UNKNOWN.getDisplayName(), SwingConstants.CENTER);
        statusBoxLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        statusBoxLabel.setOpaque(true);
        statusBoxLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                new EmptyBorder(8, 20, 8, 20)
        ));

        // 🟢 2. יצירת לבל הספירה לאחור (עיצוב אדום ובולט)
        disconnectLabel = new JLabel("", SwingConstants.CENTER);
        disconnectLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        disconnectLabel.setForeground(new Color(220, 53, 69));
        disconnectLabel.setOpaque(true);
        disconnectLabel.setBackground(new Color(255, 235, 235));
        disconnectLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 53, 69), 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));
        disconnectLabel.setVisible(false); // מוסתר כברירת מחדל

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        topPanel.add(statusBoxLabel);
        topPanel.add(disconnectLabel); // 🟢 הוספת ה-label לראשי הממשק
        frame.add(topPanel, BorderLayout.NORTH);

        imageLabel = new JLabel();
        imageLabel.setPreferredSize(new Dimension(initialWidth, initialHeight));
        frame.add(imageLabel, BorderLayout.CENTER);

        // 🟢 3. הרשמה לאירועי התנתקות דרך GameEventBus
        registerDisconnectListeners();
        registerGameOver();
    }

    public void init(NetworkController controller) {
        // עדכון הקופסה לפי התפקיד הנוכחי ב-Controller
        updateRole(controller.getRole());

        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                BoardOffset offset = boardOffset;
                int pixelX = e.getX() - offset.x();
                int pixelY = e.getY() - offset.y();

                int col = geometry.columnAt(pixelX);
                int row = geometry.rowAt(pixelY);

                if (SwingUtilities.isLeftMouseButton(e)) {
                    controller.click(col, row);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    controller.jump(col, row);
                }
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // 🟢 4. מאזינים ואירועי ספירה לאחור
    private void registerDisconnectListeners() {
        GameEventBus.getInstance().subscribe("DISCONNECT_COUNTDOWN", data -> SwingUtilities.invokeLater(() -> {
            if (data instanceof Integer seconds) {
                startUiCountdown(seconds);
            }
        }));

        GameEventBus.getInstance().subscribe("DISCONNECT_CANCELLED", data -> SwingUtilities.invokeLater(this::stopUiCountdown));
    }
    private void registerGameOver(){
        // בתוך הבנאי של GameWindow או בשיטה ייעודית לרגיסטרציית אירועים:
        GameEventBus.getInstance().subscribe("GAME_OVER", data -> SwingUtilities.invokeLater(() -> {
            Object[] payload = (Object[]) data;
            String winner = (String) payload[0];
            String reason = payload.length > 1 ? (String) payload[1] : null;

            handleGameOver(winner, reason);
        }));
    }
    private void handleGameOver(String winner, String reason) {
        stopUiCountdown();

        String message;
        if (winner != null && !winner.isBlank()) {
            message = "GAME OVER!\n\nWinner: " + winner;
            if ("RESIGN_DISCONNECT".equals(reason)) {
                message += "\n(Opponent disconnected and timed out)";
            }
        } else {
            message = "GAME OVER!\n\nIt's a Draw!";
        }
        JOptionPane.showMessageDialog(
                frame,
                message,
                "Game Over",
                JOptionPane.INFORMATION_MESSAGE
        );
        frame.dispose();
    }

    private void startUiCountdown(int initialSeconds) {
        if (uiCountdownTimer != null && uiCountdownTimer.isRunning()) {
            uiCountdownTimer.stop();
        }

        final int[] remaining = {initialSeconds};
        disconnectLabel.setText("Opponent disconnected! Resigning in: " + remaining[0] + "s");
        disconnectLabel.setVisible(true);

        uiCountdownTimer = new Timer(1000, e -> {
            remaining[0]--;
            if (remaining[0] > 0) {
                disconnectLabel.setText("Opponent disconnected! Resigning in: " + remaining[0] + "s");
            } else {
                ((Timer) e.getSource()).stop();
                disconnectLabel.setText("Opponent timed out!");
            }
        });
        uiCountdownTimer.start();
    }

    private void stopUiCountdown() {
        if (uiCountdownTimer != null && uiCountdownTimer.isRunning()) {
            uiCountdownTimer.stop();
        }
        disconnectLabel.setText("Opponent reconnected!");
        new Timer(2000, e -> {
            disconnectLabel.setVisible(false);
            ((Timer) e.getSource()).stop();
        }).start();
    }

    /**
     * 🎨 עדכון הטקסט והצבעים של הקופסה הקבועה לפי ה-Enum
     */
    public void updateRole(Role role) {
        SwingUtilities.invokeLater(() -> {
            statusBoxLabel.setText(role.getDisplayName());
            switch (role) {
                case WHITE -> {
                    statusBoxLabel.setBackground(new Color(245, 245, 245));
                    statusBoxLabel.setForeground(new Color(20, 20, 20));
                }
                case BLACK -> {
                    statusBoxLabel.setBackground(new Color(40, 40, 40));
                    statusBoxLabel.setForeground(new Color(240, 240, 240));
                }
                case SPECTATOR -> {
                    statusBoxLabel.setBackground(new Color(225, 238, 255));
                    statusBoxLabel.setForeground(new Color(15, 75, 160));
                }
                default -> {
                    statusBoxLabel.setBackground(Color.LIGHT_GRAY);
                    statusBoxLabel.setForeground(Color.DARK_GRAY);
                }
            }
        });
    }

    public void updateBoardOffsets(int boardX, int boardY) {
        this.boardOffset = new BoardOffset(boardX, boardY);
    }

    public int getWidth() { return imageLabel.getWidth(); }
    public int getHeight() { return imageLabel.getHeight(); }

    public void updateFrame(Img currentFrame) {
        SwingUtilities.invokeLater(() -> imageLabel.setIcon(new ImageIcon(currentFrame.get())));
    }
}