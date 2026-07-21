package org.example.view;

import org.example.bus.GameEventBus;
import org.example.engines.GameHistoryManager;
import org.example.engines.GameSnapshot;

/**
 * Composes one full window frame: background, board, coordinate labels and
 * both player panels.
 */
public class GameFrameComposer {
    private final ImgRenderer boardRenderer;
    private final GameHistoryManager historyManager;
    private final BoardGeometry geometry;
    private final ScoreManager scoreManager;

    private final BoardLayoutCalculator layoutCalculator = new BoardLayoutCalculator();
    private final FrameRenderer frameRenderer;

    private volatile BoardLayoutCalculator.Metrics lastLayout;

    private final String username1;
    private final String username2;

    public GameFrameComposer(ImgRenderer boardRenderer, GameHistoryManager historyManager,
                              BoardGeometry geometry, ScoreManager scoreManager, String username1, String username2) {
        this.boardRenderer = boardRenderer;
        this.historyManager = historyManager;
        this.geometry = geometry;
        this.scoreManager = scoreManager;
        this.frameRenderer = new FrameRenderer(geometry);
        this.username1 = username1;
        this.username2 = username2;

        // א. אירוע: מצב הלוח השתנה -> מרכיבים פריים חדש מלא (לוח+פאנלים) ומפרסמים אותו.
        // ה-composer לא מכיר את GameWindow בכלל - הוא רק שם על ה-bus את התוצאה המוכנה.
        GameEventBus.getInstance().subscribe("BOARD_UPDATE", data -> {
            BoardUpdatePayload payload = (BoardUpdatePayload) data;
            Img newFrame = composeFrame(payload.snapshot(), payload.windowWidth(), payload.windowHeight());
            GameEventBus.getInstance().publish("FRAME_READY", newFrame);
        });

        // ב. אירוע: בוצעה אכילה (עדכון הניקוד)
        GameEventBus.getInstance().subscribe("PIECE_CAPTURED", data -> {
            Object[] captureData = (Object[]) data;
            char capturedType = (char) captureData[0];
            char capturingColor = (char) captureData[1];

            // הפעלת הלוגיקה של ה-scoreManager הקיים שלך - הוא לא משתנה בכלל
            scoreManager.onPieceCaptured(capturedType, capturingColor);

            // הניקוד השתנה - אבל כדי לרנדר פריים חדש צריך snapshot עדכני, לא רק את הניקוד.
            // לכן: GameEngine צריך לפרסם BOARD_UPDATE (עם ה-snapshot העדכני) אחרי כל אכילה,
            // לא רק PIECE_CAPTURED - אז ה-handler למעלה כבר יטפל ברינדור. אין צורך לרנדר כאן שוב.
        });

        // ג. אירוע: מהלך בוצע (עדכון היסטוריה)
// ג. עדכון היסטוריית המהלכים
        GameEventBus.getInstance().subscribe("MOVE_LOGGED", data -> {
            // הנתונים מגיעים מהשרת כמערך או אובייקט שמכיל את כל המידע על המהלך
            Object[] moveData = (Object[]) data;
            String time = (String) moveData[0];      // הזמן שבו בוצע המהלך
            String move = (String) moveData[1];  // הטקסט של המהלך (למשל "e2e4")
            char color = (char) moveData[2];     // צבע השחקן (למשל 'w' או 'b')

            // 🚀 קריאה למתודה המדויקת שלך עם שלושת הפרמטרים!
            historyManager.onMoveAdded(time, move, color);
        });

        /*TODO: adding sound*/
        GameEventBus.getInstance().subscribe("PLAY_SOUND", data -> {
            String soundType = (String) data; // "move", "capture", "check"
            // קוד קצר שמנגן קובץ אודיו בהתאם לסוג
            // SoundPlayer.play(soundType + ".wav");
        });
    }

    /**
     * Payload for the BOARD_UPDATE topic. GameEngine (or whatever publishes
     * this event) needs to supply the current snapshot plus the window size
     * the composer should render at - the composer itself doesn't own the
     * live window dimensions, only GameWindow does.
     */
    public record BoardUpdatePayload(GameSnapshot snapshot, int windowWidth, int windowHeight) {}

    public int getBoardX() { return lastLayout != null ? lastLayout.boardX() : 0; }
    public int getBoardY() { return lastLayout != null ? lastLayout.boardY() : 0; }

    public Img composeFrame(GameSnapshot snapshot, int currentWindowWidth, int currentWindowHeight) {
        long frameTime = System.currentTimeMillis();
        BoardLayoutCalculator.Metrics layout = layoutCalculator.calculate(currentWindowWidth, currentWindowHeight);
        geometry.updateSize(layout.boardSize());
        this.lastLayout = layout;

        Img masterFrame = new Img().createEmpty(layout.windowWidth(), layout.windowHeight(), true);

        frameRenderer.drawBackground(masterFrame, layout.windowWidth(), layout.windowHeight());

        Img boardImg = boardRenderer.drawGame(snapshot, frameTime);
        boardImg.drawOn(masterFrame, layout.boardX(), layout.boardY());

        frameRenderer.drawColumnLabels(masterFrame, layout, true);
        frameRenderer.drawColumnLabels(masterFrame, layout, false);
        frameRenderer.drawRowLabels(masterFrame, layout);

        frameRenderer.drawPlayerPanel(masterFrame, username2+" - Black", scoreManager.getBlackScore(), historyManager.getBlackMoves(),
                layout.leftPanelX(), layout.boardY(), layout.boardSize(), layout.panelWidth());
        frameRenderer.drawPlayerPanel(masterFrame, username1+" - White", scoreManager.getWhiteScore(), historyManager.getWhiteMoves(),
                layout.rightPanelX(), layout.boardY(), layout.boardSize(), layout.panelWidth());

        return masterFrame;
    }
}
