package org.example.network.client;

import static org.example.network.client.JsonFields.asList;
import static org.example.network.client.JsonFields.asMap;
import static org.example.network.client.JsonFields.charValue;
import static org.example.network.client.JsonFields.intValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.Position;
import org.example.models.State;

/**
 * Turns the raw "snapshot" JSON object from a BOARD_UPDATE message into a
 * {@link GameSnapshot}. This used to be a private method on
 * ChessWebSocketClient; it's pulled out because it's pure data-mapping with
 * no networking in it at all, and is the easiest piece of that class to
 * unit-test in isolation (feed it a Map, check the GameSnapshot it returns)
 * once you don't need a live socket to exercise it.
 *
 * Also fixes a small duplication in the original: position and
 * targetPosition were parsed with two separate copy-pasted blocks that
 * differed only by field name. They now share {@link #positionFromMap}.
 */
final class GameSnapshotMapper {

    private GameSnapshotMapper() {}

    static GameSnapshot fromMap(Map<String, Object> snapshotMap) {
        List<PieceSnapshot> pieces = new ArrayList<>();
        List<Object> piecesList = asList(snapshotMap.get("pieces"));

        if (piecesList != null) {
            for (Object item : piecesList) {
                pieces.add(pieceFromMap(asMap(item)));
            }
        }

        return new GameSnapshot(pieces, readGameOverFlag(snapshotMap));
    }

    private static PieceSnapshot pieceFromMap(Map<String, Object> pieceMap) {
        int id = intValue(pieceMap.get("id"));
        char type = charValue(pieceMap.get("type"));
        char color = charValue(pieceMap.get("color"));

        Position position = positionFromMap(asMap(pieceMap.get("position")));
        Position targetPosition = positionFromMap(asMap(pieceMap.get("targetPosition")));
        State state = State.valueOf((String) pieceMap.get("state"));

        return new PieceSnapshot(id, type, color, position, targetPosition, state);
    }

    private static Position positionFromMap(Map<String, Object> posMap) {
        int row = intValue(posMap.get("row"));
        int col = intValue(posMap.get("column"));
        return new Position(row, col);
    }

    /**
     * The server has historically sent this flag under two different names
     * ("isGameOver" and "gameOver"); preserved here exactly as the original
     * fallback worked, since we don't know which servers/versions are still
     * sending the older name.
     */
    private static boolean readGameOverFlag(Map<String, Object> snapshotMap) {
        Object flag = snapshotMap.get("isGameOver");
        if (flag == null) {
            flag = snapshotMap.get("gameOver");
        }
        return flag instanceof Boolean isGameOver && isGameOver;
    }
}
