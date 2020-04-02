package org.gradle.sample.integtest.app;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.gradle.sample.app.Main;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class MainIntegrationTest {
    @Test
    public void testMain() {
        PrintStream savedOut = System.out;
        try {
            ByteArrayOutputStream outStreamForTesting = new ByteArrayOutputStream();
            System.setOut(new PrintStream(outStreamForTesting));

            Main.main(new String[0]);

            assertEquals("Hello, World!\n", outStreamForTesting.toString());
        } finally {
            System.setOut(savedOut);
        }
    }
}
