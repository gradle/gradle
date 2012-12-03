package org.gradle;

import java.io.File;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class OkTest {
    static {
        System.out.println("class loaded");
    }

    public OkTest() {
        System.out.println("test constructed");
    }

    @org.junit.BeforeClass public static void init() {
        System.out.println("before class out");
        System.err.println("before class err");
    }

    @org.junit.AfterClass public static void end() {
        System.out.println("after class out");
        System.err.println("after class err");
    }

    @org.junit.Test
    public void ok() throws Exception {
        // check versions of dependencies
        assertEquals("4.8.2", new org.junit.runner.JUnitCore().getVersion());
        assertTrue(org.apache.tools.ant.Main.getAntVersion().contains("1.6.1"));

        // check working dir
        assertEquals(System.getProperty("projectDir"), System.getProperty("user.dir"));

        // check sys properties
        assertEquals("value", System.getProperty("testSysProperty"));

        // check env vars
        assertEquals("value", System.getenv("TEST_ENV_VAR"));

        // check classloader and classpath
        assertSame(ClassLoader.getSystemClassLoader(), getClass().getClassLoader());
        assertSame(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
        assertEquals(System.getProperty("java.class.path"), System.getProperty("expectedClassPath"));
        List<URL> expectedClassPath = buildExpectedClassPath(System.getProperty("expectedClassPath"));
        List<URL> actualClassPath = buildActualClassPath();
        assertEquals(expectedClassPath, actualClassPath);

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

        // check other environmental stuff
        assertEquals("Test worker", Thread.currentThread().getName());
        assertNull(System.getSecurityManager());

        // check stdout and stderr and logging
        System.out.println("This is test stdout");
        System.out.println("non-asci char: ż");
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

    private List<URL> buildActualClassPath() {
        List<URL> urls = Arrays.asList(((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs());
        return urls.subList(1, urls.size());
    }

    private List<URL> buildExpectedClassPath(String expectedClassPath) throws MalformedURLException {
        String[] paths = expectedClassPath.split(Pattern.quote(File.pathSeparator));
        List<URL> urls = new ArrayList<URL>();
        for (String path : paths) {
            urls.add(new File(path).toURI().toURL());
        }
        return urls;
    }

    @org.junit.Test
    public void anotherOk() {
        System.out.println("sys out from another test method");
        System.err.println("sys err from another test method");
    }
}
