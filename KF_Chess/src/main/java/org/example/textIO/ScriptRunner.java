package org.example.textIO;

import org.example.models.Board;
import org.example.controllers.Controller;
import org.example.engines.GameEngine;
import org.example.realtime.RealTimeArbiter;


/**
 * Parses and executes simple script commands for board setup and actions.
 */
public class ScriptRunner {

    private Board board;
    private StringBuilder boardString = new StringBuilder();
    private boolean readingBoard = false;
    private GameEngine gameEngine;
    private Controller controller;
    private RealTimeArbiter rta;

    /**
     * Process a single input line from the script.
     *
     * @param line input line containing board data or a command
     */
    public void handleInputLine(String line) {
        if (line.equalsIgnoreCase("Board:")) {
            readingBoard = true;
            return;
        }

        if (line.equalsIgnoreCase("Commands:")) {
            board = BoardParser.parse(boardString.toString());
            rta = new RealTimeArbiter();
            gameEngine = new GameEngine(board, rta);
            controller = new Controller(gameEngine);
            readingBoard = false;
            return;
        }

        if (readingBoard) {
            appendBoardRow(line);
        } else {
            processCommand(line);
        }
    }

    private void appendBoardRow(String line) {
        boardString.append(line).append('\n');
    }

    private void processCommand(String line) {
        String[] cmdTokens = line.split("\\s+");
        if (cmdTokens.length == 0) {
            return;
        }

        String command = cmdTokens[0].toLowerCase();
        switch (command) {
            case "print":
                if (cmdTokens.length > 1 && cmdTokens[1].equalsIgnoreCase("board")) {
                    BoardPrinter.print(board);
                }
                break;

            case "click":
                if (cmdTokens.length == 3) {
                    handleClickCommand(cmdTokens);
                }
                break;

            case "wait":
                if (cmdTokens.length == 2) {
                    handleWaitCommand(cmdTokens);
                }
                break;

            case "jump":
                if (cmdTokens.length == 3) {
                    handleJumpCommandParsed(cmdTokens);
                }
                break;

            default:
                System.err.println("Unknown command: " + line);
                break;
        }
    }

    private void handleClickCommand(String[] cmdTokens) {
        try {
            int x = Integer.parseInt(cmdTokens[1]);
            int y = Integer.parseInt(cmdTokens[2]);
            controller.click(x, y);
        } catch (NumberFormatException e) {
            System.err.println("Invalid coordinate format in command: " + String.join(" ", cmdTokens));
        }
    }

    private void handleWaitCommand(String[] cmdTokens) {
        try {
            long delay = Long.parseLong(cmdTokens[1]);
            controller.wait_(delay);
        } catch (NumberFormatException e) {
            System.err.println("Invalid wait time: " + cmdTokens[1]);
        }
    }

    private void handleJumpCommandParsed(String[] cmdTokens) {
        try {
            int x = Integer.parseInt(cmdTokens[1]);
            int y = Integer.parseInt(cmdTokens[2]);
            controller.jump(x, y);
        } catch (NumberFormatException e) {
            System.err.println("Invalid coordinate format in command: " + String.join(" ", cmdTokens));
        }
    }
}
