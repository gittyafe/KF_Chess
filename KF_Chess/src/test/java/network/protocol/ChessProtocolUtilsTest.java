package network.protocol;

import org.example.engines.PieceSnapshot;
import org.example.models.Position;
import org.example.models.State;
import org.example.network.protocol.ChessProtocolUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChessProtocolUtilsTest {

    private static PieceSnapshot piece(char type, char color, Position pos) {
        // State's actual constant names aren't known from the files shared,
        // so we grab whichever value is first rather than hardcoding one
        // that might not exist.
        State anyState = State.values()[0];
        return new PieceSnapshot(1, type, color, pos, pos, anyState);
    }

    @Test
    void toNotation_topLeftCorner_isA8() {
        assertEquals("a8", ChessProtocolUtils.toNotation(new Position(0, 0)));
    }

    @Test
    void toNotation_bottomLeftCorner_isA1() {
        assertEquals("a1", ChessProtocolUtils.toNotation(new Position(7, 0)));
    }

    @Test
    void toNotation_bottomRightCorner_isH1() {
        assertEquals("h1", ChessProtocolUtils.toNotation(new Position(7, 7)));
    }

    @Test
    void toNotation_middleSquare_e4() {
        // column 'e' = index 4, rank 4 means row = 8 - 4 = 4
        assertEquals("e4", ChessProtocolUtils.toNotation(new Position(4, 4)));
    }

    @Test
    void buildMoveCommand_whitePiece_usesUppercaseW() {
        PieceSnapshot pawn = piece('P', 'w', new Position(6, 4)); // e2
        String command = ChessProtocolUtils.buildMoveCommand(pawn, new Position(4, 4)); // e4

        assertEquals("WPe2e4", command);
    }

    @Test
    void buildMoveCommand_blackPiece_usesUppercaseB() {
        PieceSnapshot pawn = piece('p', 'b', new Position(1, 4)); // e7
        String command = ChessProtocolUtils.buildMoveCommand(pawn, new Position(3, 4)); // e5

        assertEquals("BPe7e5", command);
    }

    @Test
    void buildMoveCommand_lowercaseColorInput_isNormalizedToUppercase() {
        // color char itself isn't required to be exactly 'W'/'B' going in --
        // toUpperCase(color) == 'W' decides, anything else becomes 'B'.
        PieceSnapshot knight = piece('N', 'x', new Position(7, 1));
        String command = ChessProtocolUtils.buildMoveCommand(knight, new Position(5, 2));

        assertTrue(command.startsWith("B")); // 'x' uppercased isn't 'W', so falls to 'B'
    }

    @Test
    void buildJumpCommand_whitePiece() {
        PieceSnapshot knight = piece('N', 'w', new Position(7, 1)); // b1
        String command = ChessProtocolUtils.buildJumpCommand(knight);

        assertEquals("JWb1", command);
    }

    @Test
    void buildJumpCommand_blackPiece() {
        PieceSnapshot knight = piece('n', 'b', new Position(0, 1)); // b8
        String command = ChessProtocolUtils.buildJumpCommand(knight);

        assertEquals("JBb8", command);
    }
}
