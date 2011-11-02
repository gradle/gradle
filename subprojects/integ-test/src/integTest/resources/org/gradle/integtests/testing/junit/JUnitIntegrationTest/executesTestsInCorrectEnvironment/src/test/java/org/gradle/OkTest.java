package org.gradle;

import static org.junit.Assert.*;

import java.io.PrintStream;
import java.util.logging.Logger;

public class OkTest {
    static {
        System.out.println("class loaded");
    }

    public OkTest() {
        System.out.println("test constructed");
    }

    @org.junit.Test
    public void ok() throws Exception {
        // check JUnit version
        assertEquals("4.8.2", new org.junit.runner.JUnitCore().getVersion());
        // check Ant version
        assertTrue(org.apache.tools.ant.Main.getAntVersion().contains("1.6.1"));
        // check working dir
        assertEquals(System.getProperty("projectDir"), System.getProperty("user.dir"));
        // check classloader
        assertSame(ClassLoader.getSystemClassLoader(), getClass().getClassLoader());
        assertSame(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
        // check Gradle and impl classes not visible
        try {
            getClass().getClassLoader().loadClass("org.gradle.api.Project");
            fail();
        } catch (ClassNotFoundException e) {
        }
        try {
            getClass().getClassLoader().loadClass("org.slf4j.Logger");
            fail();
        } catch (ClassNotFoundException e) {
        }
        // check sys properties
        assertEquals("value", System.getProperty("testSysProperty"));
        // check env vars
        assertEquals("value", System.getenv("TEST_ENV_VAR"));

        // check stdout and stderr and logging
        System.out.println("This is test stdout");
        System.out.print("no EOL");
        System.out.println();
        System.err.println("This is test stderr");
        Logger.getLogger("test-logger").warning("this is a warning");

        final PrintStream out = System.out;
        // logging from a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                out.println("stdout from a shutdown hook.");
                Logger.getLogger("test-logger").info("info from a shutdown hook.");
            }
        });

        // logging from another thread
        Thread thread = new Thread() {
            @Override
            public void run() {
                System.out.println("stdout from another thread");
                Logger.getLogger("test-logger").info("info from another thread.");
            }
        };
        thread.start();
        thread.join();
    }
}
