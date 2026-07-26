package network.client;

import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.State;
import org.example.network.client.GameSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GameSnapshotMapper.fromMap is package-private and static, so this test
 * (in the same package) calls it directly. State's real constant names
 * weren't part of the files shared, so tests round-trip through
 * State.values()[0]'s own .name() rather than guessing a constant.
 */
class GameSnapshotMapperTest {

    private static Map<String, Object> positionMap(int row, int col) {
        Map<String, Object> m = new HashMap<>();
        m.put("row", row);
        m.put("column", col);
        return m;
    }

    private static Map<String, Object> pieceMap(int id, char type, char color, String state,
                                                  Map<String, Object> position, Map<String, Object> target) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("type", String.valueOf(type));
        m.put("color", String.valueOf(color));
        m.put("state", state);
        m.put("position", position);
        m.put("targetPosition", target);
        return m;
    }

    @Test
    void fromMap_noPieces_returnsEmptyPieceList() {
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("pieces", List.of());
        snapshotMap.put("isGameOver", false);

        GameSnapshot snapshot = GameSnapshotMapper.fromMap(snapshotMap);

        assertTrue(snapshot.pieces().isEmpty());
        assertFalse(snapshot.isGameOver());
    }

    @Test
    void fromMap_missingPiecesKey_returnsEmptyPieceList() {
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("isGameOver", false);

        GameSnapshot snapshot = GameSnapshotMapper.fromMap(snapshotMap);

        assertTrue(snapshot.pieces().isEmpty());
    }

    @Test
    void fromMap_parsesPieceFields() {
        String stateName = State.values()[0].name();
        Map<String, Object> piece = pieceMap(7, 'P', 'W', stateName, positionMap(6, 4), positionMap(4, 4));
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("pieces", List.of(piece));
        snapshotMap.put("isGameOver", false);

        GameSnapshot snapshot = GameSnapshotMapper.fromMap(snapshotMap);

        assertEquals(1, snapshot.pieces().size());
        PieceSnapshot parsed = snapshot.pieces().get(0);
        assertEquals(7, parsed.id());
        assertEquals('P', parsed.type());
        assertEquals('W', parsed.color());
        assertEquals(State.valueOf(stateName), parsed.state());
        assertEquals(6, parsed.position().getRow());
        assertEquals(4, parsed.position().getColumn());
        assertEquals(4, parsed.targetPosition().getRow());
        assertEquals(4, parsed.targetPosition().getColumn());
    }

    @Test
    void fromMap_isGameOverTrue_isReadCorrectly() {
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("pieces", List.of());
        snapshotMap.put("isGameOver", true);

        assertTrue(GameSnapshotMapper.fromMap(snapshotMap).isGameOver());
    }

    @Test
    void fromMap_fallsBackToGameOverKey_whenIsGameOverMissing() {
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("pieces", List.of());
        snapshotMap.put("gameOver", true);

        assertTrue(GameSnapshotMapper.fromMap(snapshotMap).isGameOver());
    }

    @Test
    void fromMap_neitherGameOverKeyPresent_defaultsFalse() {
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("pieces", List.of());

        assertFalse(GameSnapshotMapper.fromMap(snapshotMap).isGameOver());
    }

    @Test
    void fromMap_gameOverFlagNotBoolean_treatedAsFalse() {
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("pieces", List.of());
        snapshotMap.put("isGameOver", "true"); // string, not boolean

        assertFalse(GameSnapshotMapper.fromMap(snapshotMap).isGameOver());
    }

    @Test
    void fromMap_multiplePieces_allParsed() {
        String stateName = State.values()[0].name();
        Map<String, Object> p1 = pieceMap(1, 'P', 'W', stateName, positionMap(6, 0), positionMap(4, 0));
        Map<String, Object> p2 = pieceMap(2, 'p', 'B', stateName, positionMap(1, 0), positionMap(3, 0));
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("pieces", List.of(p1, p2));
        snapshotMap.put("isGameOver", false);

        GameSnapshot snapshot = GameSnapshotMapper.fromMap(snapshotMap);

        assertEquals(2, snapshot.pieces().size());
    }
}
