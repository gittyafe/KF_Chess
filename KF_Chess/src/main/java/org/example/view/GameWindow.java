package org.example.view;


import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.example.Img;
import org.example.controllers.Controller;

public class GameWindow {
    private final JFrame frame;
    private final JLabel imageLabel;
    private final int boardOffsetX;
    private final int boardOffsetY;

    public GameWindow(String title, int width, int height, int boardOffsetX, int boardOffsetY) {
        this.boardOffsetX = boardOffsetX;
        this.boardOffsetY = boardOffsetY;
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        imageLabel = new JLabel();
        imageLabel.setPreferredSize(new Dimension(width, height));
        frame.add(imageLabel);
    }

    public void init(Controller controller) {
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int boardX = e.getX() - boardOffsetX;
                int boardY = e.getY() - boardOffsetY;

                // התעלמות מקליקים מחוץ לתחום הלוח (אופציונלי אך מומלץ)
                if (boardX < 0 || boardY < 0) return;

                if (SwingUtilities.isLeftMouseButton(e)) {
                    controller.click(boardX, boardY);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    controller.jump(boardX, boardY);
                }
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void updateFrame(Img currentFrame) {
        SwingUtilities.invokeLater(() -> {
            imageLabel.setIcon(new ImageIcon(currentFrame.get()));
        });
    }
}