package org.example.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.Img;
import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.State;

public class ImgRenderer {
    private static final long DEFAULT_ANIMATION_DURATION_MS = 300;
    private static final float STATE_LABEL_FONT_SIZE = 0.8f;

    private final String boardPath;
    private final BoardGeometry geometry;
    private final PieceImageLoader imageLoader;
    private final Map<Integer, LogicalAnimation> activeAnimations = new HashMap<>();

    // מטמון תמונת הלוח הבסיסית - נטענת/מרוסקלת מחדש רק כשהגודל באמת משתנה,
    // במקום בכל פריים (30ms). זה קורא IO ורסקול-תמונה שקודם קרו ~33 פעם/שנייה
    // בזמן גרירת שינוי גודל, וגרמו לפריימים "לפגר" אחרי הגודל האמיתי -
    // מה שנתן תחושה של לוח/כלים שלא תואמים למיקום הלחיצה בפועל.
    private BufferedImage cachedBaseBoard;
    private int cachedBoardSize = -1;

    public ImgRenderer(String boardPath, BoardGeometry geometry, PieceImageLoader imageLoader) {
        this.boardPath = boardPath;
        this.geometry = geometry;
        this.imageLoader = imageLoader;
    }

    public Img drawGame(GameSnapshot snapshot) {
        long frameTime = System.currentTimeMillis();

        int size = geometry.getBoardSizePx();
        BufferedImage isolatedBuffer = isolatedBoardCopy(size);
        Graphics2D g2d = isolatedBuffer.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 2. עדכון מצבי האנימציות הלוגיות
        updateAnimations(snapshot.pieces(), frameTime);

        // 3. ציור הכלים ישירות על הבאפר המבודד
        for (PieceSnapshot piece : snapshot.pieces()) {
            LogicalAnimation anim = activeAnimations.get(piece.id());
            long startTime = (anim != null) ? anim.stateStartTime : frameTime;

            String folderName = mapStateToFolderName(piece.state());
            AnimationConfig config = imageLoader.getAnimation(piece.color(), piece.type(), folderName);

            if (config == null || config.getFrames().isEmpty()) continue;
            Img pieceImg = config.getCurrentFrame(startTime, frameTime);
            if (pieceImg == null) continue;

            // שינוי גודל של תמונת הכלי
            java.awt.Dimension targetSize = geometry.getPieceTargetSize();
            Img resizedPieceImg = scaleImage(pieceImg, targetSize.width, targetSize.height);

            // חישוב מיקום יחסי רספונסיבי מדויק (לפי משבצות לוגיות)
            double col = (anim != null) ? anim.currentCol : piece.position().getColumn();
            double row = (anim != null) ? anim.currentRow : piece.position().getRow();

            int x = (int) (col * geometry.getCellSize()) + (geometry.getCellSize() - targetSize.width) / 2;
            int y = (int) (row * geometry.getCellSize()) + (geometry.getCellSize() - targetSize.height) / 2;

            // מניעת חריגה מגבולות הבאפר
            int maxAllowedX = size - targetSize.width;
            int maxAllowedY = size - targetSize.height;
            x = Math.max(0, Math.min(x, maxAllowedX));
            y = Math.max(0, Math.min(y, maxAllowedY));

            // ציור הכלי על ה-Graphics המבודד של הפריים הנוכחי
            g2d.drawImage(resizedPieceImg.get(), x, y, null);

            // ציור הטקסט של המצב
            g2d.setColor(Color.RED);
            g2d.setFont(g2d.getFont().deriveFont(12.0f));
            g2d.drawString(piece.state().toString(), x + 5, y + geometry.getCellSize() - 5);
        }

        g2d.dispose();

        // 4. עטיפת הבאפר המבודד והסופי באובייקט Img והחזרתו
        Img finalFrame = new Img();
        finalFrame.set(isolatedBuffer);
        return finalFrame;
    }

    /**
     * מחזיר עותק "מבודד" (isolated) של תמונת הלוח בגודל הנתון, לציור הפריים
     * הנוכחי עליו. הלוח הבסיסי עצמו נטען מהדיסק ומרוסקל רק כשהגודל משתנה
     * לעומת הפעם הקודמת - לא בכל קריאה. העתקת הבאפר במטמון היא זולה
     * (זיכרון בלבד) לעומת קריאת קובץ + רסקול שהיו קורים כאן קודם בכל פריים.
     */
    private BufferedImage isolatedBoardCopy(int size) {
        if (cachedBaseBoard == null || cachedBoardSize != size) {
            Img baseBoard = new Img().read(boardPath, new java.awt.Dimension(size, size), false, null);
            BufferedImage fresh = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = fresh.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(baseBoard.get(), 0, 0, null);
            g.dispose();
            cachedBaseBoard = fresh;
            cachedBoardSize = size;
        }

        BufferedImage copy = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(cachedBaseBoard, 0, 0, null);
        g.dispose();
        return copy;
    }

    private void updateAnimations(List<PieceSnapshot> snapshots, long frameTime) {
        Set<Integer> activeIds = new HashSet<>();

        for (PieceSnapshot snapshot : snapshots) {
            int id = snapshot.id();
            activeIds.add(id);

            LogicalAnimation anim = activeAnimations.get(id);

            if (anim == null) {
                anim = new LogicalAnimation(snapshot.position().getColumn(), snapshot.position().getRow(), snapshot.state(), frameTime);
                activeAnimations.put(id, anim);
            }

            if (anim.state != snapshot.state()) {
                anim.state = snapshot.state();
                anim.stateStartTime = frameTime;
            }

            int targetCol = snapshot.targetPosition().getColumn();
            int targetRow = snapshot.targetPosition().getRow();

            if (anim.targetCol != targetCol || anim.targetRow != targetRow) {
                long duration = getDurationForState(snapshot);
                if (duration <= 10) duration = DEFAULT_ANIMATION_DURATION_MS;
                anim.setNewTarget(snapshot.position().getColumn(), snapshot.position().getRow(), targetCol, targetRow, duration, frameTime);
            }

            anim.update(frameTime);
        }

        activeAnimations.keySet().retainAll(activeIds);
    }

    private Img scaleImage(Img original, int targetWidth, int targetHeight) {
        if (original.get().getWidth() == targetWidth && original.get().getHeight() == targetHeight) {
            return original;
        }
        Img newImg = new Img().createEmpty(targetWidth, targetHeight, true);
        Graphics2D g = newImg.get().createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original.get(), 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return newImg;
    }

    private long getDurationForState(PieceSnapshot snapshot) {
        String folderName = mapStateToFolderName(snapshot.state());
        AnimationConfig cfg = imageLoader.getAnimation(snapshot.color(), snapshot.type(), folderName);
        return (cfg != null) ? cfg.getTotalDuration() : DEFAULT_ANIMATION_DURATION_MS;
    }

    private String mapStateToFolderName(State state) {
        return switch (state) {
            case JUMPING -> "jump";
            case MOVING -> "move";
            case LONG_REST -> "long_rest";
            case SHORT_REST -> "short_rest";
            case IDLE -> "idle";
            default -> "idle";
        };
    }

    private static class LogicalAnimation {
        public State state;
        public long stateStartTime;
        public long startTime;
        public long duration;

        public double currentCol;
        public double currentRow;

        public int startCol, startRow;
        public int targetCol, targetRow;

        public LogicalAnimation(int col, int row, State state, long frameTime) {
            this.state = state;
            this.stateStartTime = frameTime;
            this.startTime = frameTime;
            this.duration = 0;
            this.startCol = col;
            this.startRow = row;
            this.targetCol = col;
            this.targetRow = row;
            this.currentCol = col;
            this.currentRow = row;
        }

        public void setNewTarget(int fromCol, int fromRow, int toCol, int toRow, long duration, long frameTime) {
            this.startTime = frameTime;
            this.duration = duration;
            this.startCol = fromCol;
            this.startRow = fromRow;
            this.targetCol = toCol;
            this.targetRow = toRow;
        }

        public void update(long frameTime) {
            if (duration <= 0) {
                currentCol = targetCol;
                currentRow = targetRow;
                return;
            }
            double progress = (double) (frameTime - startTime) / duration;
            progress = Math.max(0.0, Math.min(1.0, progress));

            currentCol = startCol + (targetCol - startCol) * progress;
            currentRow = startRow + (targetRow - startRow) * progress;
        }
    }
}
