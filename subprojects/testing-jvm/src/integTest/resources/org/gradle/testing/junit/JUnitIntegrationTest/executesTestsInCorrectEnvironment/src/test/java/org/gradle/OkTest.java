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
        boolean isJava9 = Boolean.valueOf(System.getProperty("isJava9"));
        String classPathString = System.getProperty("java.class.path");
        String expectedClassPathString = System.getProperty("expectedClassPath");
        if (isJava9) {
            String[] splitClasspath = splitClasspath(classPathString);
            String workerJar = splitClasspath[0];
            String[] classpathWithoutWorkerJar = Arrays.copyOfRange(splitClasspath, 1, splitClasspath.length);
            assertTrue(workerJar + " is the worker jar", workerJar.contains("gradle-worker.jar"));
            assertEquals(splitClasspath(expectedClassPathString), classpathWithoutWorkerJar);
        } else {
            assertEquals(expectedClassPathString, classPathString);
            List<URL> expectedClassPath = buildExpectedClassPath(expectedClassPathString);
            List<URL> actualClassPath = buildActualClassPath();
            assertEquals(expectedClassPath, actualClassPath);
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
        assertNull(System.getSecurityManager());

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

    private List<URL> buildActualClassPath() {
        List<URL> urls = Arrays.asList(((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs());
        return urls.subList(1, urls.size());
    }

    private List<URL> buildExpectedClassPath(String expectedClassPath) throws MalformedURLException {
        String[] paths = splitClasspath(expectedClassPath);
        List<URL> urls = new ArrayList<URL>();
        for (String path : paths) {
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
