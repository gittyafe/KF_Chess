import models.Board;
import textIO.BoardParser;
import textIO.BoardPrinter;

public class ScriptRunner {

    private Board board;
    private String boardString = "";
    private boolean readingBoard = false;
    private GameEngine gameEngine;
    private Controller controller;
    private RealTimeEngine rta;

    public void handleInputLine(String line) {
        if (line.equalsIgnoreCase("Board:")) {
            readingBoard = true;
            return;
        }
        if (line.startsWith("Commands")) {
            this.board = BoardParser.parse(boardString);
            rta = new RealTimeEngine();
            gameEngine = new GameEngine(board, rta);
            controller = new Controller(gameEngine);
            readingBoard = false;
            return;
        }

        if (readingBoard) {
            parseAndAddRow(line);
        } else {
            processCommand(line);
        }
    }

    private void parseAndAddRow(String line) {
        boardString += (line + "\n");
    }

    private void processCommand(String line) {
        String[] cmdTokens = line.split("\\s+");
        String command = cmdTokens[0].toLowerCase();

        if (command.equals("print") && cmdTokens.length > 1 && cmdTokens[1].equalsIgnoreCase("board")) {
            BoardPrinter.print(board);
        } else if (command.equals("click") && cmdTokens.length == 3) {
            handleClickCommand(cmdTokens);
        } else if (command.equals("wait") && cmdTokens.length == 2) {
            handleWaitCommand(cmdTokens);
        } else if (command.equals("jump") && cmdTokens.length == 3) {
            handleJumpCommandParsed(cmdTokens);
        }
    }

    private void handleClickCommand(String[] cmdTokens) {
        try {
            int x = Integer.parseInt(cmdTokens[1]);
            int y = Integer.parseInt(cmdTokens[2]);
            controller.click(x, y);
        } catch (NumberFormatException e) {
        }
    }

    private void handleWaitCommand(String[] cmdTokens) {
        gameEngine.wait_(Long.parseLong(cmdTokens[1]));
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
