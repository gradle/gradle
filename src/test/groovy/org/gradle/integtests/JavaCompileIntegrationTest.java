package org.gradle.integtests;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.io.File;

public class JavaCompileIntegrationTest extends AbstractIntegrationTest {

    private File testDir;

    @Before
    public void setUp() {
        testDir= testFile("javacompiletest");
    }

    private void writeShortInterface() {
        testFile(testDir, "src/main/java/IPerson.java").writelns(
                "interface IPerson {",
                "    String getName();",
                "}"
        );
    }

    private void writeLongInterface() {
        testFile(testDir, "src/main/java/IPerson.java").writelns(
                "interface IPerson {",
                "    String getName();",
                "    String getAddress();",
                "}"
        );
    }

    private void writeTestClass() {
        testFile(testDir, "src/main/java/Person.java").writelns(
                "public class Person implements IPerson {",
                "    private final String name = \"never changes\";",
                "    public String getName() {",
                "        return name;\n" +
                "    }",
                "}"
        );
    }


    @Test
    public void compileWithoutDepends() {
        testFile(testDir, "build.gradle").writelns("usePlugin('java')");
        writeShortInterface();
        writeTestClass();

        inDirectory(testDir).withTasks("compile").run();

        // Update interface, compile will pass even though build is broken
        writeLongInterface();
        inDirectory(testDir).withTasks("compile").run();

        ExecutionFailure failure = inDirectory(testDir).withTasks("clean", "compile").runWithFailure();
        failure.assertHasDescription("Execution failed for task ':compile'.");
    }

    @Test
    public void compileWithDepends() {
        testFile(testDir, "build.gradle").writelns(
                "usePlugin('java')",
                "compile.options.depend()"
        );
        writeShortInterface();
        writeTestClass();

        inDirectory(testDir).withTasks("compile").run();

        // file system time stamp may not see change without this wait
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) { }
        
        // Update interface, compile should fail because depend deletes old class
        writeLongInterface();
        ExecutionFailure failure = inDirectory(testDir).withTasks("compile").runWithFailure();
        failure.assertHasDescription("Execution failed for task ':compile'.");

        // assert that dependency caching is on
        testFile(testDir, "build/dependency-cache/dependencies.txt").assertExists();
    }



}
