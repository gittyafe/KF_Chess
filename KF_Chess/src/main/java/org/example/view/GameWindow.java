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
    private final BoardGeometry geometry;

    /**
     * תמונת מצב אטומית של מיקום פינת הלוח (בפיקסלים) בתוך החלון.
     * זה השדה שבאמת גרם ל"כלי לא נכון" בזמן ריסייז: X ו-Y היו שני שדות
     * int נפרדים שנכתבים מ-thread לולאת המשחק ונקראים מה-EDT בלחיצת עכבר,
     * בלי סנכרון - כך שהיה אפשר לקבל X מפריים אחד ו-Y מפריים אחר. עכשיו
     * שניהם ארוזים יחד ומתפרסמים אטומית דרך שדה volatile יחיד.
     */
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
                // קריאה יחידה של תמונת המצב הנוכחית - מבטיחה ש-X ו-Y
                // תמיד יגיעו מאותו פריים בדיוק, גם תוך כדי שינוי גודל מהיר
                BoardOffset offset = boardOffset;
                int pixelX = e.getX() - offset.x();
                int pixelY = e.getY() - offset.y();

                // תרגום פיקסל -> משבצת לוגית מתבצע כאן, בשכבת ה-UI, לפי
                // הגיאומטריה העדכנית ביותר (אותו snapshot שהציור עצמו
                // משתמש בו). ה-Controller מקבל רק (col, row) לוגי מוכן -
                // הוא לא צריך ולא אמור לדעת שקיימים פיקסלים.
                int col = geometry.columnAt(pixelX);
                int row = geometry.rowAt(pixelY);

                // לחיצה מחוץ לגבולות הלוח בפועל (למשל בזמן ריסייז חד) - מתעלמים
                if (col == -1 || row == -1) {
                    return;
                }

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

    /** עדכון אטומי של מיקום פינת הלוח, נקרא מה-thread של לולאת המשחק */
    public void updateBoardOffsets(int boardX, int boardY) {
        this.boardOffset = new BoardOffset(boardX, boardY);
    }

    // קריאה ישירה בכוונה: שדות הגודל של JComponent מתעדכנים סינכרונית
    // ע"י ה-peer כחלק מטיפול הריסייז עצמו, עוד לפני שכל componentResized
    // אסינכררוני נשלח - זו הדרך היחידה לקבל גודל "חי" תוך כדי גרירה.
    public int getWidth() { return imageLabel.getWidth(); }
    public int getHeight() { return imageLabel.getHeight(); }

    public void updateFrame(Img currentFrame) {
        SwingUtilities.invokeLater(() -> imageLabel.setIcon(new ImageIcon(currentFrame.get())));
    }
}
