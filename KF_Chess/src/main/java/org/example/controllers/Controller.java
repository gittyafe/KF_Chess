package org.example.controllers;

import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.MoveRequest;
import org.example.models.Position;
import org.example.engines.GameEngine;
import org.example.network.ChessWebSocketClient;

/**
 * Manages piece selection and move initiation.
 *
 * <p>Has two modes:</p>
 * <ul>
 *   <li><b>Local mode</b> ({@link #Controller(GameEngine)}) - the original
 *   behavior. Clicks are validated and applied directly against a local
 *   {@link GameEngine}. Kept as-is for any purely local/offline use.</li>
 *   <li><b>Networked mode</b> ({@link #Controller(ChessWebSocketClient)}) -
 *   no local GameEngine at all. Clicks are translated into move commands and
 *   sent to the server; the server is the only authority on the game state.
 *   Click hit-testing uses whatever {@link GameSnapshot} the server most
 *   recently sent (see {@link #updateSnapshot(GameSnapshot)}), so what you
 *   click always matches what's actually rendered.</li>
 * </ul>
 */
public class Controller {

    private boolean isSelected = false;
    private Position selectedPosition = new Position(-1, -1);

    private final GameEngine gameEngine;              // non-null only in local mode
    private final ChessWebSocketClient networkClient;  // non-null only in networked mode

    private volatile GameSnapshot latestSnapshot;       // networked mode only

    /** Local mode - clicks are applied directly to this GameEngine. */
    public Controller(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        this.networkClient = null;
    }

    /** Networked mode - clicks are sent to the server as move commands. */
    public Controller(ChessWebSocketClient networkClient) {
        this.gameEngine = null;
        this.networkClient = networkClient;
    }

    /**
     * Feed the latest snapshot received from the server so click hit-testing
     * (networked mode) matches what's currently on screen. Call this every
     * time a BOARD_UPDATE arrives. No-op in local mode.
     */
    public void updateSnapshot(GameSnapshot snapshot) {
        this.latestSnapshot = snapshot;
    }

    /**
     * Process a click at a logical board cell and either select a piece or
     * attempt a move.
     *
     * @param col logical column index on the board (0-based)
     * @param row logical row index on the board (0-based)
     */
    public void click(int col, int row) {
        Position targetPosition = new Position(row, col);

        if (networkClient != null) {
            clickNetworked(targetPosition);
            return;
        }

        clickLocal(targetPosition);
    }

    private void clickLocal(Position targetPosition) {
        if (!isSelected) {
            if (gameEngine.getPieceAt(targetPosition) != null) {
                isSelected = true;
                selectedPosition = targetPosition;
            }
            return;
        }

        MoveRequest moveResult = gameEngine.requestMove(selectedPosition, targetPosition);
        switch (moveResult.getReason()) {
            case SUCCESS:
                clearSelection();
                break;

            case SAME_COLOR_OCCUPIED:
                selectedPosition = targetPosition;
                break;

            default:
                clearSelection();
                break;
        }
    }

    private void clickNetworked(Position targetPosition) {
        if (!isSelected) {
            if (findPieceAt(targetPosition) != null) {
                isSelected = true;
                selectedPosition = targetPosition;
            }
            return;
        }

        PieceSnapshot selectedPiece = findPieceAt(selectedPosition);
        if (selectedPiece == null) {
            // The piece we had selected isn't there anymore in the latest
            // snapshot (moved / captured since we selected it) - nothing
            // sensible to send, just reset instead of guessing.
            clearSelection();
            return;
        }

        // Reselecting your own piece instead of moving, same as local mode's
        // SAME_COLOR_OCCUPIED behavior.
        PieceSnapshot targetPiece = findPieceAt(targetPosition);
        if (targetPiece != null && targetPiece.color() == selectedPiece.color()) {
            selectedPosition = targetPosition;
            return;
        }

        networkClient.sendMoveCommand(buildMoveCommand(selectedPiece, targetPosition));
        clearSelection();
    }

    private PieceSnapshot findPieceAt(Position position) {
        GameSnapshot snapshot = latestSnapshot;
        if (snapshot == null || position == null) {
            return null;
        }
        for (PieceSnapshot piece : snapshot.pieces()) {
            if (position.equals(piece.position())) {
                return piece;
            }
        }
        return null;
    }

    // בונה פקודה בדיוק בפורמט ש-ChessWebSocketHandler מצפה לו בצד השרת:
    // תו צבע ('W'/'B') + תו נוסף (לא בשימוש היום בשרת) + משבצת מקור + משבצת יעד.
    private String buildMoveCommand(PieceSnapshot piece, Position to) {
        char colorChar = Character.toUpperCase(piece.color()) == 'W' ? 'W' : 'B';
        char typeChar = Character.toUpperCase(piece.type());
        return "" + colorChar + typeChar + toNotation(piece.position()) + toNotation(to);
    }

    // הופך Position(row, col) לסימון כמו "e2" - הפעולה ההפוכה בדיוק ל-
    // ChessWebSocketHandler.parseNotation בצד השרת.
    private String toNotation(Position pos) {
        char file = (char) ('a' + pos.getColumn());
        char rank = (char) ('0' + (8 - pos.getRow()));
        return "" + file + rank;
    }

    /**
     * Clear the currently selected piece.
     */
    public void clearSelection() {
        isSelected = false;
        selectedPosition = new Position(-1, -1);
    }

    /**
     * Start a jump action for the piece at the given logical board cell.
     *
     * @param col logical column index on the board (0-based)
     * @param row logical row index on the board (0-based)
     */
    public void jump(int col, int row) {
        Position position = new Position(row, col);

        if (networkClient != null) {
            jumpNetworked(position);
            return;
        }

        gameEngine.jumpRequest(position);
    }

    private void jumpNetworked(Position position) {
        PieceSnapshot piece = findPieceAt(position);
        if (piece == null) {
            return; // nothing there to jump
        }
        networkClient.sendMoveCommand(buildJumpCommand(piece));
    }

    // בונה פקודת קפיצה בדיוק בפורמט ש-ChessWebSocketHandler מצפה לו:
    // 'J' + תו צבע ('W'/'B') + משבצת.
    private String buildJumpCommand(PieceSnapshot piece) {
        char colorChar = Character.toUpperCase(piece.color()) == 'W' ? 'W' : 'B';
        return "J" + colorChar + toNotation(piece.position());
    }

    public void wait_(long ms) {
        if (gameEngine != null) {
            gameEngine.wait_(ms);
        }
        // Networked mode: nothing local to advance - the server's own tick
        // loop drives the game, and BOARD_UPDATE snapshots keep us in sync.
    }
}
