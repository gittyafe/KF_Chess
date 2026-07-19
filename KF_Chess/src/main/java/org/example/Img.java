package org.example;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Lightweight image‑utility class using only standard JDK APIs.
 */
public class Img {

    private BufferedImage img;

    /* ----------- load & optional resize ----------- */
    public Img read(String path,
                    Dimension targetSize,
                    boolean keepAspect,
                    Object interpolation /*ignored*/) {

        try {
            img = ImageIO.read(new File(path));                              // :contentReference[oaicite:0]{index=0}
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load image: " + path);
        }
        if (img == null) throw new IllegalArgumentException("Unsupported image: " + path);

        if (targetSize != null) {
            int tw = targetSize.width, th = targetSize.height;
            int w = img.getWidth(), h = img.getHeight();

            int nw, nh;
            if (keepAspect) {                                                // :contentReference[oaicite:1]{index=1}
                double s = Math.min(tw / (double) w, th / (double) h);
                nw = (int) Math.round(w * s);
                nh = (int) Math.round(h * s);
            } else { nw = tw; nh = th; }

            BufferedImage dst = new BufferedImage(
                    nw, nh,
                    img.getColorModel().hasAlpha()
                            ? BufferedImage.TYPE_INT_ARGB
                            : BufferedImage.TYPE_INT_RGB);

            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                               RenderingHints.VALUE_INTERPOLATION_BILINEAR);   // :contentReference[oaicite:2]{index=2}
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            img = dst;
        }
        return this;
    }

    public Img read(String path) { return read(path, null, false, null); }

    /* ----------- draw this image onto another ----------- */
    public void drawOn(Img other, int x, int y) {
        if (img == null || other.img == null)
            throw new IllegalStateException("Both images must be loaded.");

        if (x + img.getWidth()  > other.img.getWidth()
         || y + img.getHeight() > other.img.getHeight())
            throw new IllegalArgumentException("Patch exceeds destination bounds.");

        Graphics2D g = other.img.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);                               // handles alpha channel :contentReference[oaicite:3]{index=3}
        g.drawImage(img, x, y, null);                                        // :contentReference[oaicite:4]{index=4}
        g.dispose();
    }

    /* ----------- annotate with text ----------- */
    public void putText(String txt, int x, int y, float fontSize,
                        Color color, int thickness /*unused in Java2D*/) {

        if (img == null) throw new IllegalStateException("Image not loaded.");

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(color);
        g.setFont(img.getGraphics().getFont().deriveFont(fontSize * 12));     // simple scale
        g.drawString(txt, x, y);                                             // :contentReference[oaicite:5]{index=5}
        g.dispose();
    }

    /* ----------- display in a Swing window ----------- */
    public void show() {
        if (img == null) throw new IllegalStateException("Image not loaded.");

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Image");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(new JLabel(new ImageIcon(img)));                            // :contentReference[oaicite:6]{index=6}
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    /* ----------- access (optional) ----------- */
    public BufferedImage get() { return img; }

    /* ----------- create an empty image (helper) ----------- */
    public Img createEmpty(int width, int height, boolean withAlpha) {
        img = new BufferedImage(width, height, withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // fill with transparent or black background
        if (withAlpha) {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0,0,width,height);
            g.setComposite(AlphaComposite.SrcOver);
        } else {
            g.setColor(Color.BLACK);
            g.fillRect(0,0,width,height);
        }
        g.dispose();
        return this;
    }

    /**
     * Draws a rectangle OUTLINE (stroke only). Despite the old comment on this
     * method ("filled or outline"), it never actually fills - it always calls
     * {@code Graphics2D.drawRect}, never {@code fillRect}. Any caller that
     * wanted a solid painted area (a background, a panel, a header strip...)
     * and used this method got an empty outlined box instead. Use
     * {@link #fillRect} for solid fills; this method is for actual outlines.
     */
    public void drawRect(int x, int y, int width, int height, Color color) {
        float thickness = 2.0f; // ערך ברירת מחדל
        if (this.img == null) {
            throw new IllegalStateException("Image not loaded."); // [cite: 95]
        }

        Graphics2D graphics = this.img.createGraphics();
        graphics.setColor(color); // [cite: 96]
        graphics.setStroke(new BasicStroke(thickness)); // [cite: 96]
        graphics.drawRect(x, y, width, height); // [cite: 96]
        graphics.dispose(); // [cite: 96]
    }

    /** Draws a solid, filled rectangle - the "paint a background/panel" operation {@link #drawRect} does not do. */
    public void fillRect(int x, int y, int width, int height, Color color) {
        if (this.img == null) {
            throw new IllegalStateException("Image not loaded.");
        }

        Graphics2D graphics = this.img.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(x, y, width, height);
        graphics.dispose();
    }

    /**
     * Returns a new, independently-scaled {@link Img} of this image at the
     * given size (bilinear interpolation). This image itself is left
     * untouched, which matters for callers holding on to a shared/cached
     * source image (e.g. a piece's animation frame reused across draws).
     */
    public Img resize(int width, int height) {
        if (this.img == null) {
            throw new IllegalStateException("Image not loaded.");
        }
        if (this.img.getWidth() == width && this.img.getHeight() == height) {
            return this;
        }

        BufferedImage dst = new BufferedImage(
                width, height,
                this.img.getColorModel().hasAlpha()
                        ? BufferedImage.TYPE_INT_ARGB
                        : BufferedImage.TYPE_INT_RGB);

        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(this.img, 0, 0, width, height, null);
        g.dispose();

        Img result = new Img();
        result.img = dst;
        return result;
    }

    public Img copy() {
        if (this.img == null) {
            throw new IllegalStateException("Image not loaded."); // [cite: 97]
        }

        BufferedImage copy = new BufferedImage(
                this.img.getWidth(),
                this.img.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        ); // [cite: 98]

        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(this.img, 0, 0, null); // [cite: 98]
        graphics.dispose(); // [cite: 98]

        Img result = new Img();
        result.img = copy; // [cite: 99, 100]
        return result;
    }

    public void drawLine(int x1, int y1, int x2, int y2, Color color) {
        if (this.img == null) {
            throw new IllegalStateException("Image not loaded.");
        }

        Graphics2D graphics = this.img.createGraphics();
        graphics.setColor(color);
        // ניתן להגדיר עובי קו אם רוצים, נשתמש ב-1.0f כברירת מחדל
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.drawLine(x1, y1, x2, y2);
        graphics.dispose();
    }

    public void set(BufferedImage isolatedBuffer) {
        // השדה הפנימי שמחזיק את ה-BufferedImage בתוך מחלקת Img שלך (בדרך כלל נקרא image או img)
        this.img = isolatedBuffer;
    }
}
