import java.util.Scanner;

import models.Board;
import textIO.BoardParser;
import textIO.BoardPrinter;

public class Main {
    static boolean readingBoard=false;
    static String boardString = "";
    static Board board;
    public static void handleInputLine(String line) {
        if (line.equalsIgnoreCase("Board:")) {
            readingBoard = true;
            return;
        }
        if (line.startsWith("Commands")) {
            board = BoardParser.parse(boardString);
            readingBoard = false;
            return;
        }

        if (readingBoard) {
            parseAndAddRow(line);
        } else {
            BoardPrinter.print(board);
        }
    }
  
    private static void parseAndAddRow(String line) {
        boardString += (line+"\n");
        
    }


    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                handleInputLine(line);
            }
        }
        scanner.close();
    }

}
