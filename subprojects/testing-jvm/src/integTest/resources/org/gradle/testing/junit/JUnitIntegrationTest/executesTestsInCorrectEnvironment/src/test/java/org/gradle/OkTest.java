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
    @org.junit.Test
    public void ok() throws Exception {
        // check versions of dependencies
        assertEquals("4.13", new org.junit.runner.JUnitCore().getVersion());
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
        boolean isJava9 = Boolean.parseBoolean(System.getProperty("isJava9"));
        String[] splitTestRuntimeClasspath = splitClasspath(System.getProperty("testRuntimeClasspath"));
        if (isJava9) {
            String[] splitCliClasspath = splitClasspath(System.getProperty("java.class.path"));

            // The worker jar is first on the classpath.
            String workerJar = splitCliClasspath[0];
            assertTrue(workerJar + " is the worker jar", workerJar.contains("gradle-worker.jar"));

            // After, we expect the test runtime classpath.
            String[] filteredCliClasspath = Arrays.copyOfRange(splitCliClasspath, 1, splitCliClasspath.length);
            assertEquals(splitTestRuntimeClasspath, filteredCliClasspath);
        } else {
            List<URL> systemClasspath = Arrays.asList(((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs());

            // The worker jar is first on the classpath.
            String workerJar = systemClasspath.get(0).getPath();
            assertTrue(workerJar + " is the worker jar", workerJar.endsWith("gradle-worker.jar"));

            // After, we expect the test runtime classpath.
            List<URL> filteredSystemClasspath = systemClasspath.subList(1, systemClasspath.size());
            List<URL> testRuntimeClasspath = getTestRuntimeClasspath(splitTestRuntimeClasspath);
            assertEquals(testRuntimeClasspath, filteredSystemClasspath);
        }

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
        assertNull(System.console());

        final PrintStream out = System.out;
        // logging from a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                out.println("stdout from a shutdown hook.");
                Logger.getLogger("test-logger").info("info from a shutdown hook.");
            }
        });
    }

    public List<URL> getTestRuntimeClasspath(String[] splitTestRuntimeClasspath) throws MalformedURLException {
        List<URL> urls = new ArrayList<URL>();
        for (String path : splitTestRuntimeClasspath) {
            urls.add(new File(path).toURI().toURL());
        }
        return urls;
    }

    private String[] splitClasspath(String classpath) {
        return classpath.split(Pattern.quote(File.pathSeparator));
    }

    @org.junit.Test
    public void anotherOk() {
    }
}
