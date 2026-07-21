package org.example.controllers;

import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.Position;
import org.example.network.ChessWebSocketClient;
import org.example.network.ChessProtocolUtils; // מחלקת עזר להמרת הפורמט

public class NetworkController {

    private final ChessWebSocketClient networkClient;
    private final SelectionManager selectionManager = new SelectionManager();
    private volatile GameSnapshot latestSnapshot;

    public NetworkController(ChessWebSocketClient networkClient) {
        this.networkClient = networkClient;
    }

    public void updateSnapshot(GameSnapshot snapshot) {
        this.latestSnapshot = snapshot;
    }

    public void click(int col, int row) {
        Position targetPosition = new Position(row, col);

        // 1. אם לא נבחר כלי עדיין - בודקים אם לחצו על כלי כלשהו
        if (!selectionManager.isSelected()) {
            if (findPieceAt(targetPosition) != null) {
                selectionManager.select(targetPosition);
            }
            return;
        }

        // 2. כלי כבר נבחר בעבר
        Position selectedPos = selectionManager.getSelectedPosition();
        PieceSnapshot selectedPiece = findPieceAt(selectedPos);

        // אם הכלי הנבחר נעלם מלוח המשחק בזמן שחיכינו (למשל נלכד ע"י השחקן השני)
        if (selectedPiece == null) {
            selectionManager.clear();
            return;
        }

        // 3. אם לחצו על כלי אחר באותו צבע - מעבירים את הבחירה אליו
        PieceSnapshot targetPiece = findPieceAt(targetPosition);
        if (targetPiece != null && targetPiece.color() == selectedPiece.color()) {
            selectionManager.select(targetPosition);
            return;
        }

        // 4. במידה ולחצו על משבצת יעד חוקית כביכול - שולחים פקודה לשרת
        String command = ChessProtocolUtils.buildMoveCommand(selectedPiece, targetPosition);
        networkClient.sendMoveCommand(command);

        // מאפסים את הבחירה המקומית ומחכים לעדכון השרת ב-updateSnapshot
        selectionManager.clear();
    }

    public void jump(int col, int row) {
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
}