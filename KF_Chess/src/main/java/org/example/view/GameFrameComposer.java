package org.example.view;

import java.awt.Color;
import org.example.Img;
import org.example.models.State;
import org.example.engines.GameSnapshot;

public class GameFrameComposer {
    private final ImgRenderer boardRenderer;

    public GameFrameComposer(ImgRenderer boardRenderer) {
        this.boardRenderer = boardRenderer;
    }

    public Img composeFrame(GameSnapshot snapshot) {
        // 1. ציור לוח המשחק (מתודה נקייה ללא צורך בהעברת שעון חיצוני)
        Img boardImg = boardRenderer.drawGame(snapshot);

        // 2. יצירת קנבס מאסטר ריק בגודל החלון כפי שהגדרת במקור
        int masterW = Math.max(1100, boardImg.get().getWidth());
        int masterH = Math.max(800, boardImg.get().getHeight());
        Img masterFrame = new Img().createEmpty(masterW, masterH, true);

        // 3. הדבקת הלוח המרונדר בצד שמאל (X=0, Y=0)
        boardImg.drawOn(masterFrame, 0, 0);

        // 4. כתיבת נתוני הסטטיסטיקה והסיידבר בצד ימין (החל מ-X=825)
        int startX = 825;
        masterFrame.putText("KUNG FU CHESS", startX, 60, 1.8f, new Color(212, 175, 55), 2);
        masterFrame.putText("REAL-TIME", startX, 95, 1.0f, Color.LIGHT_GRAY, 1);

        masterFrame.putText("GAME STATUS:", startX, 160, 1.1f, Color.WHITE, 1);
        if (snapshot.isGameOver()) {
            masterFrame.putText("GAME OVER", startX, 200, 1.4f, Color.RED, 2);
        } else {
            masterFrame.putText("ACTIVE", startX, 200, 1.2f, Color.GREEN, 1);
        }

        long whiteCount = snapshot.pieces().stream().filter(p -> p.color() == 'w').count();
        long blackCount = snapshot.pieces().stream().filter(p -> p.color() == 'b').count();
        long activeMotions = snapshot.pieces().stream().filter(p -> p.state() != State.IDLE).count();

        masterFrame.putText("REMAINING PIECES:", startX, 280, 1.1f, Color.WHITE, 1);
        masterFrame.putText("White: " + whiteCount, startX, 320, 0.9f, Color.LIGHT_GRAY, 0);
        masterFrame.putText("Black: " + blackCount, startX, 350, 0.9f, Color.LIGHT_GRAY, 0);

        masterFrame.putText("PHYSICS ACTIVE:", startX, 430, 1.1f, Color.WHITE, 1);
        masterFrame.putText("In Motion: " + activeMotions, startX, 470, 0.9f,
                activeMotions > 0 ? Color.ORANGE : Color.LIGHT_GRAY, 0);

        masterFrame.putText("CONTROLS:", startX, 660, 1.0f, new Color(212, 175, 55), 1);
        masterFrame.putText("L-Click: Move / Target", startX, 700, 0.8f, Color.GRAY, 0);
        masterFrame.putText("R-Click: Special Jump", startX, 730, 0.8f, Color.GRAY, 0);

        return masterFrame;
    }
}