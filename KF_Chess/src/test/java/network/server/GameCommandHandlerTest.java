package network.server;

import org.example.engines.GameEngine;
import org.example.models.Piece;
import org.example.models.Position;
import org.example.network.server.game.GameCommandHandler;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.PlayerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GameCommandHandlerTest {

    private GameCommandHandler handler;
    private GameRoom room;
    private GameEngine engine;

    @BeforeEach
    void setUp() {
        handler = new GameCommandHandler();
        room = mock(GameRoom.class);
        engine = mock(GameEngine.class);
        when(room.getGameEngine()).thenReturn(engine);
    }

    @Test
    void wellFormedMove_forCorrectColorPiece_requestsMove() {
        PlayerInfo white = new PlayerInfo("alice", 'W');
        Piece pawn = mock(Piece.class);
        when(pawn.getColor()).thenReturn('w');
        when(engine.getPieceAt(any(Position.class))).thenReturn(pawn);

        handler.handle(room, white, "WPe2e4");

        verify(engine).requestMove(any(Position.class), any(Position.class));
    }

    @Test
    void move_wrongColorPiece_isIgnored() {
        PlayerInfo white = new PlayerInfo("alice", 'W');
        Piece blackPawn = mock(Piece.class);
        when(blackPawn.getColor()).thenReturn('b');
        when(engine.getPieceAt(any(Position.class))).thenReturn(blackPawn);

        handler.handle(room, white, "WPe2e4");

        verify(engine, never()).requestMove(any(), any());
    }

    @Test
    void move_noPieceAtSquare_isIgnored() {
        PlayerInfo white = new PlayerInfo("alice", 'W');
        when(engine.getPieceAt(any(Position.class))).thenReturn(null);

        handler.handle(room, white, "WPe2e4");

        verify(engine, never()).requestMove(any(), any());
    }

    @Test
    void malformedMove_wrongLength_isIgnored() {
        PlayerInfo white = new PlayerInfo("alice", 'W');

        handler.handle(room, white, "WPe2e4x"); // 7 chars, not 6

        verifyNoInteractions(engine);
    }

    @Test
    void malformedMove_invalidSquare_isIgnored() {
        PlayerInfo white = new PlayerInfo("alice", 'W');

        handler.handle(room, white, "WPz9e4"); // 'z' file, '9' rank invalid

        verifyNoInteractions(engine);
    }

    @Test
    void malformedMove_invalidColorChar_isIgnored() {
        PlayerInfo white = new PlayerInfo("alice", 'W');

        handler.handle(room, white, "XPe2e4"); // 'X' isn't W or B

        verifyNoInteractions(engine);
    }

    @Test
    void wellFormedJump_forCorrectColorPiece_requestsJump() {
        PlayerInfo black = new PlayerInfo("bob", 'B');
        Piece checker = mock(Piece.class);
        when(checker.getColor()).thenReturn('b');
        when(engine.getPieceAt(any(Position.class))).thenReturn(checker);

        handler.handle(room, black, "JBe4");

        verify(engine).jumpRequest(any(Position.class));
    }

    @Test
    void jump_wrongColorPiece_isIgnored() {
        PlayerInfo black = new PlayerInfo("bob", 'B');
        Piece whitePiece = mock(Piece.class);
        when(whitePiece.getColor()).thenReturn('w');
        when(engine.getPieceAt(any(Position.class))).thenReturn(whitePiece);

        handler.handle(room, black, "JBe4");

        verify(engine, never()).jumpRequest(any());
    }

    @Test
    void malformedJump_wrongLength_isIgnored() {
        PlayerInfo black = new PlayerInfo("bob", 'B');

        handler.handle(room, black, "JBe45"); // 5 chars, not 4

        verifyNoInteractions(engine);
    }

    @Test
    void malformedJump_invalidColorChar_isIgnored() {
        PlayerInfo black = new PlayerInfo("bob", 'B');

        handler.handle(room, black, "JXe4"); // second char must be W/B

        verifyNoInteractions(engine);
    }

    @Test
    void handle_lowercaseLeadingChar_isTreatedCaseInsensitively() {
        PlayerInfo black = new PlayerInfo("bob", 'B');
        Piece checker = mock(Piece.class);
        when(checker.getColor()).thenReturn('b');
        when(engine.getPieceAt(any(Position.class))).thenReturn(checker);

        handler.handle(room, black, "jBe4"); // lowercase 'j'

        verify(engine).jumpRequest(any(Position.class));
    }

    @Test
    void engineThrows_exceptionIsCaught_doesNotPropagate() {
        PlayerInfo white = new PlayerInfo("alice", 'W');
        Piece pawn = mock(Piece.class);
        when(pawn.getColor()).thenReturn('w');
        when(engine.getPieceAt(any(Position.class))).thenReturn(pawn);
        doThrow(new RuntimeException("engine error")).when(engine).requestMove(any(), any());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> handler.handle(room, white, "WPe2e4"));
    }
}
