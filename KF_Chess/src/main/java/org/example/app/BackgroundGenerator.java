package org.example.app;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class BackgroundGenerator {
    public static void main(String[] args) {
        try {
            // 1. יצירת תמונה בזיכרון בגודל המלא 1100x800
            BufferedImage img = new BufferedImage(1100, 800, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();

            // 2. צביעת הצד השמאלי (הלוח) בשחור פשוט (מפיקסל 0 עד 800)
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 800, 800);

            // 3. צביעת הסיידבר הימני באפור כהה (מפיקסל 800 עד 1100)
            g.setColor(new Color(30, 30, 36));
            g.fillRect(800, 0, 300, 800);

            // 4. ציור פס ההפרדה המוזהב בפיקסל 800 (ברוחב 4 פיקסלים)
            g.setColor(new Color(212, 175, 55));
            g.fillRect(800, 0, 4, 800);

            g.dispose();

            // 5. שמירת התמונה ישירות לתוך תיקיית ה-resources שלך
            File outputFile = new File("src/main/resources/main_background.png");
            outputFile.getParentFile().mkdirs(); // יוצר את התיקייה אם היא לא קיימת

            ImageIO.write(img, "png", outputFile);

            System.out.println("Success! The image 'main_background.png' has been created in your resources folder.");
        } catch (Exception e) {
            System.err.println("Failed to create background image: " + e.getMessage());
        }
    }
}
