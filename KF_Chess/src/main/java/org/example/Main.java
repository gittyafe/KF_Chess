package org.example;

import java.util.Scanner;
import org.example.textIO.ScriptRunner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ScriptRunner scriptRunner = new ScriptRunner();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                try{
                scriptRunner.handleInputLine(line);
                }catch(Exception e){
                    System.out.println(e.getMessage());
                }
            }
        }
        scanner.close();
    }

}
