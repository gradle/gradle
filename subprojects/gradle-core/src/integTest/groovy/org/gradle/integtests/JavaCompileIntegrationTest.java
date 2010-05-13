/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests;

import org.gradle.integtests.fixtures.ExecutionFailure;
import org.junit.Test;

public class JavaCompileIntegrationTest extends AbstractIntegrationTest {

    private void writeShortInterface() {
        testFile("src/main/java/IPerson.java").writelns(
                "interface IPerson {",
                "    String getName();",
                "}"
        );
    }

    private void writeLongInterface() {
        testFile("src/main/java/IPerson.java").writelns(
                "interface IPerson {",
                "    String getName();",
                "    String getAddress();",
                "}"
        );
    }

    private void writeTestClass() {
        testFile("src/main/java/Person.java").writelns(
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
        testFile("build.gradle").writelns("apply plugin: 'java'");
        writeShortInterface();
        writeTestClass();

        inTestDirectory().withTasks("classes").run();

        // Update interface, compile will pass even though build is broken
        writeLongInterface();
        inTestDirectory().withTasks("classes").run();

        ExecutionFailure failure = inTestDirectory().withTasks("clean", "classes").runWithFailure();
        failure.assertHasDescription("Execution failed for task ':compileJava'.");
    }

    @Test
    public void compileWithDepends() {
        testFile("build.gradle").writelns(
                "apply plugin: 'java'",
                "compileJava.options.depend()"
        );
        writeShortInterface();
        writeTestClass();

        inTestDirectory().withTasks("classes").run();

        // file system time stamp may not see change without this wait
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        
        // Update interface, compile should fail because depend deletes old class
        writeLongInterface();
        ExecutionFailure failure = inTestDirectory().withTasks("classes").runWithFailure();
        failure.assertHasDescription("Execution failed for task ':compileJava'.");

        // assert that dependency caching is on
        testFile("build/dependency-cache/dependencies.txt").assertExists();
    }
}
