package com.aerospike.example;

import java.awt.*;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.awt.event.KeyEvent;

public class Console {
//    public static void click(int x, int y) throws AWTException{
//        Robot bot = new Robot();
//        bot.mouseMove(x, y);
//        bot.mousePress(InputEvent.BUTTON1_MASK);
//        bot.mouseRelease(InputEvent.BUTTON1_MASK);
//    }
    public static void clear() {
//        try {
//            click(75,890);
//        } catch (AWTException e) {
//            e.printStackTrace();
//        }

        // Map Clear All to CONTROL+SHIFT+C
        // https://stackoverflow.com/questions/46102201/is-it-possible-to-clear-the-console-tab-during-runtime-in-intellij-with-java
        try {
            Robot robot = new Robot();
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_C);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.keyRelease(KeyEvent.VK_C);
        } catch (AWTException ex) {
            ex.printStackTrace(System.err);
        }
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
