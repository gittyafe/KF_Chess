package org.example.view;

import java.awt.Color;

import org.example.Img;
import org.example.models.State;
import org.example.engines.GameSnapshot;


public class GameFrameComposer {
    private final ImgRenderer boardRenderer;

    // הבנאי לא צריך שום קובץ רקע יותר!
    public GameFrameComposer(ImgRenderer boardRenderer) {
        this.boardRenderer = boardRenderer;
    }

    public Img composeFrame(GameSnapshot snapshot) {
        // 1. ניצור אובייקט Img חדש לגמרי (שהוא בגודל החלון 1100x800)
        // 2. נצייר את הלוח העדכני (גובה/רוחב משתנים)
        Img boardImg = boardRenderer.drawGame(snapshot);

        // Ensure master frame is at least as big as boardImg
        int masterW = Math.max(1100, boardImg.get().getWidth());
        int masterH = Math.max(800, boardImg.get().getHeight());
        Img masterFrame = new Img().createEmpty(masterW, masterH, true);

        // 3. נדביק את לוח בצד שמאל (X=0, Y=0)
        boardImg.drawOn(masterFrame, 0, 0);

        // 4. כתיבת הטקסטים של הסיידבר בצד ימין (החל מ-X=825)
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