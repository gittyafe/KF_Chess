package org.example.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.example.controllers.NetworkController;
import org.example.models.Role;

public class GameWindow {
    private final JFrame frame;
    private final JLabel imageLabel;
    private final JLabel statusBoxLabel; // 📦 הקופסה הקבועה להצגת התפקיד
    private final BoardGeometry geometry;

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

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topPanel.add(statusBoxLabel);
        frame.add(topPanel, BorderLayout.NORTH);

        imageLabel = new JLabel();
        imageLabel.setPreferredSize(new Dimension(initialWidth, initialHeight));
        frame.add(imageLabel, BorderLayout.CENTER);
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

    /**
     * 🎨 עדכון הטקסט והצבעים של הקופסה הקבועה לפי ה-Enum
     */
    public void updateRole(Role role) {
        SwingUtilities.invokeLater(() -> {
            statusBoxLabel.setText("Your Role: " + role.getDisplayName());

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