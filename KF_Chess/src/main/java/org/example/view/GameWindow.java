package org.example.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.example.controllers.Controller;

public class GameWindow {
    private final JFrame frame;
    private final JLabel imageLabel;
    private final BoardGeometry geometry;

    private record BoardOffset(int x, int y) {}
    private volatile BoardOffset boardOffset = new BoardOffset(0, 0);

    public GameWindow(String title, int initialWidth, int initialHeight, BoardGeometry geometry) {
        this.geometry = geometry;

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true); // מותר להגדיל ולהקטין

        imageLabel = new JLabel();
        imageLabel.setPreferredSize(new Dimension(initialWidth, initialHeight));
        frame.add(imageLabel);
    }

    public void init(Controller controller) {
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

    public void updateBoardOffsets(int boardX, int boardY) {
        this.boardOffset = new BoardOffset(boardX, boardY);
    }

    public int getWidth() { return imageLabel.getWidth(); }
    public int getHeight() { return imageLabel.getHeight(); }

    public void updateFrame(Img currentFrame) {
        SwingUtilities.invokeLater(() -> imageLabel.setIcon(new ImageIcon(currentFrame.get())));
    }
}
