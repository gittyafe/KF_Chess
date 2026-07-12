import java.util.Scanner;


public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ScriptRunner scriptRunner = new ScriptRunner();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                scriptRunner.handleInputLine(line);
            }
        }
        scanner.close();
    }

}
