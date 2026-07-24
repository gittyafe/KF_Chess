package org.example.controllers;

import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.Position;
import org.example.models.Role;
import org.example.network.client.ChessWebSocketClient;
import org.example.network.protocol.ChessProtocolUtils;

/**
 * Translates raw board input (clicks, jumps) into network move commands.
 */
public class NetworkController {

    private final ChessWebSocketClient networkClient;
    private final SelectionManager selectionManager = new SelectionManager();
    private volatile GameSnapshot latestSnapshot;
    private volatile Role role;

    public NetworkController(ChessWebSocketClient networkClient, Role role) {
        this.networkClient = networkClient;
        this.role = role != null ? role : Role.UNKNOWN;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isSpectator() {
        return role == Role.SPECTATOR;
    }

    public void updateSnapshot(GameSnapshot snapshot) {
        this.latestSnapshot = snapshot;
    }

    /**
     * Handles a board click. First click on an occupied square selects it;
     * a second click either moves the selected piece there, re-selects a
     * different piece of the same color, or (if it's an opposing piece) is
     * sent to the server as a capture move.
     */
    public void click(int col, int row) {
        if (isSpectator()) {
            return;
        }

        Position targetPosition = new Position(row, col);

        if (!selectionManager.isSelected()) {
            if (findPieceAt(targetPosition) != null) {
                selectionManager.select(targetPosition);
            }
            return;
        }

        Position selectedPos = selectionManager.getSelectedPosition();
        PieceSnapshot selectedPiece = findPieceAt(selectedPos);

        if (selectedPiece == null) {
            selectionManager.clear();
            return;
        }

        PieceSnapshot targetPiece = findPieceAt(targetPosition);
        if (targetPiece != null && targetPiece.color() == selectedPiece.color()) {
            selectionManager.select(targetPosition);
            return;
        }

        networkClient.sendMoveCommand(ChessProtocolUtils.buildMoveCommand(selectedPiece, targetPosition));
        selectionManager.clear();
    }

    /** Handles a "jump" input for whichever piece currently occupies the
     *  given square, bypassing the select-then-target click flow above. */
    public void jump(int col, int row) {
        if (isSpectator()) {
            return;
        }

        PieceSnapshot piece = findPieceAt(new Position(row, col));
        if (piece != null) {
            networkClient.sendMoveCommand(ChessProtocolUtils.buildJumpCommand(piece));
        }
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

    public GameSnapshot getLatestSnapshot() {
        return latestSnapshot;
    }
}
