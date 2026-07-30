package org.example.network.server.game;

import lombok.extern.slf4j.Slf4j;
import org.example.models.Piece;
import org.example.models.Position;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.PlayerInfo;

/**
 * Decodes the raw move/jump protocol strings (e.g. "WPe2e4", "JWe4") and
 * applies them to a room's engine on behalf of the sending player. This is
 * the server-side counterpart to ChessProtocolUtils, which encodes the same
 * commands on the client. Kept separate from MessageHandler so "how do we
 * route an incoming WS frame" and "how do we interpret the move-command
 * text protocol" don't stay tangled in one class.
 */
@Slf4j
public class GameCommandHandler {

    /**
     * Fixed bug: an empty (or null) command used to reach {@code
     * command.charAt(0)} below with no guard -- a {@code
     * StringIndexOutOfBoundsException} (or NPE) thrown there is *outside*
     * every one of {@code handleMove}/{@code handleJump}'s own try/catch
     * blocks, so it propagated straight out of {@code handle()}
     * uncaught. Depending on the caller, that could crash message
     * handling for the whole session rather than just being ignored as a
     * malformed command like every other invalid input here already is.
     */
    public void handle(GameRoom room, PlayerInfo player, String command) {
        if (command == null || command.isEmpty()) {
            log.warn("[SERVER WARN] Ignored empty/null game command from player {}", player.username());
            return;
        }

        char firstChar = Character.toUpperCase(command.charAt(0));
        if (firstChar == 'J') {
            handleJump(room, player, command);
        } else {
            handleMove(room, player, command);
        }
    }

    private void handleMove(GameRoom room, PlayerInfo player, String command) {
        if (!isWellFormedMoveCommand(command)) return;

        try {
            Position from = parseNotation(command.substring(2, 4));
            Position to = parseNotation(command.substring(4, 6));

            Piece piece = room.getGameEngine().getPieceAt(from);
            char expectedColor = player.color() == 'W' ? 'w' : 'b';

            if (piece != null && piece.getColor() == expectedColor) {
                room.getGameEngine().requestMove(from, to);
            }
        } catch (Exception e) {
            log.error("[SERVER ERROR] Error executing move: {}", e.getMessage());
        }
    }

    private void handleJump(GameRoom room, PlayerInfo player, String command) {
        if (!isWellFormedJumpCommand(command)) return;

        try {
            Position destination = parseNotation(command.substring(2, 4));

            Piece piece = room.getGameEngine().getPieceAt(destination);
            char expectedColor = player.color() == 'W' ? 'w' : 'b';

            if (piece != null && piece.getColor() == expectedColor) {
                room.getGameEngine().jumpRequest(destination);
            }
        } catch (Exception e) {
            log.error("[SERVER ERROR] Error executing jump: {}", e.getMessage());
        }
    }

    private boolean isWellFormedMoveCommand(String command) {
        if (command.length() != 6) return false;
        char colorChar = Character.toUpperCase(command.charAt(0));
        return (colorChar == 'W' || colorChar == 'B')
                && isValidSquare(command.substring(2, 4))
                && isValidSquare(command.substring(4, 6));
    }

    private boolean isWellFormedJumpCommand(String command) {
        if (command.length() != 4) return false;
        char colorChar = Character.toUpperCase(command.charAt(1));
        return (colorChar == 'W' || colorChar == 'B') && isValidSquare(command.substring(2, 4));
    }

    private boolean isValidSquare(String square) {
        if (square.length() != 2) return false;
        char file = Character.toLowerCase(square.charAt(0));
        char rank = square.charAt(1);
        return file >= 'a' && file <= 'h' && rank >= '1' && rank <= '8';
    }

    private Position parseNotation(String notation) {
        int col = notation.charAt(0) - 'a';
        int row = 8 - Character.getNumericValue(notation.charAt(1));
        return new Position(row, col);
    }
}
