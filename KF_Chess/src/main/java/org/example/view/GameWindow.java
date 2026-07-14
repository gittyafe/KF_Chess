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

    public GameWindow(String title, int width, int height) {
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        imageLabel = new JLabel();
        imageLabel.setPreferredSize(new Dimension(width, height));
        frame.add(imageLabel);
    }

    public void init(Controller controller) {
        // הוספת מאזין עכבר שמקשר ישירות ל-Controller
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    controller.click(e.getX(), e.getY());
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    controller.jump(e.getX(), e.getY());
                }
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * Updates the window image smoothly with double buffering.
     */
    public void updateFrame(Img currentFrame) {
        SwingUtilities.invokeLater(() -> {
            imageLabel.setIcon(new ImageIcon(currentFrame.get()));
        });
    }
}
