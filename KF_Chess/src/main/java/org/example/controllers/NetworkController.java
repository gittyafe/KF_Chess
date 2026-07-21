package org.example.controllers;

import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.Position;
import org.example.models.Role;
import org.example.network.ChessWebSocketClient;
import org.example.network.ChessProtocolUtils;

public class NetworkController {

    private final ChessWebSocketClient networkClient;
    private final SelectionManager selectionManager = new SelectionManager();
    private volatile GameSnapshot latestSnapshot;
    private volatile Role role; // 🎯 שימוש ב-Enum עבור תפקיד המשתמש

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

    public void click(int col, int row) {
        // 🛑 צופים לא יכולים לבצע לחיצות/מהלכים
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

        String command = ChessProtocolUtils.buildMoveCommand(selectedPiece, targetPosition);
        networkClient.sendMoveCommand(command);

        selectionManager.clear();
    }

    public void jump(int col, int row) {
        if (isSpectator()) {
            return;
        }

        Position position = new Position(row, col);
        PieceSnapshot piece = findPieceAt(position);
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