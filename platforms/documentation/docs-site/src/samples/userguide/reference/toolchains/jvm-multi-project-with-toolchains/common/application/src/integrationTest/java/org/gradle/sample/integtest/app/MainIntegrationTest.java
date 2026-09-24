package org.gradle.sample.integtest.app;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

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

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("Hello, World!");
            pw.close();

            assertEquals(sw.toString(), outStreamForTesting.toString());
        } finally {
            System.setOut(savedOut);
        }
    }
}
