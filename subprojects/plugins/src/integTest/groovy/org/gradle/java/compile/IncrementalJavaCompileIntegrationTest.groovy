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
package org.gradle.java.compile

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.junit.Rule
import org.junit.Test

class IncrementalJavaCompileIntegrationTest extends AbstractIntegrationTest {

    @Rule public final TestResources resources = new TestResources()

    @Test
    public void recompilesSourceWhenPropertiesChange() {
        executer.withTasks('compileJava').run().assertTasksSkipped()

        file('build.gradle').text += '''
            sourceCompatibility = 1.4
'''

        executer.withTasks('compileJava').run().assertTasksSkipped()

        file('build.gradle').text += '''
            compileJava.options.debug = false
'''

        executer.withTasks('compileJava').run().assertTasksSkipped()

        executer.withTasks('compileJava').run().assertTasksSkipped(':compileJava')
    }

    @Test
    public void recompilesDependentClasses() {
        executer.withTasks("classes").run();

        // Update interface, compile should fail
        file('src/main/java/IPerson.java').assertIsFile().copyFrom(file('NewIPerson.java'))
        
        ExecutionFailure failure = executer.withTasks("classes").runWithFailure();
        failure.assertHasDescription("Execution failed for task ':compileJava'.");
    }

    @Test
    public void recompilesDependentClassesAcrossProjectBoundaries() {
        executer.withTasks("app:classes").run();

        // Update interface, compile should fail
        file('lib/src/main/java/IPerson.java').assertIsFile().copyFrom(file('NewIPerson.java'))

        ExecutionFailure failure = executer.withTasks("app:classes").runWithFailure();
        failure.assertHasDescription("Execution failed for task ':app:compileJava'.");
    }

    @Test
    public void recompilesDependentClassesWhenUsingAntDepend() {
        file("build.gradle").writelns(
                "apply plugin: 'java'",
                "compileJava.options.depend()"
        );
        writeShortInterface();
        writeTestClass();

        executer.withTasks("classes").run();

        // file system time stamp may not see change without this wait
        Thread.sleep(1000L);

        // Update interface, compile should fail because depend deletes old class
        writeLongInterface();
        ExecutionFailure failure = executer.withTasks("classes").runWithFailure();
        failure.assertHasDescription("Execution failed for task ':compileJava'.");

        // assert that dependency caching is on
        file("build/dependency-cache/dependencies.txt").assertExists();
    }

    private void writeShortInterface() {
        file("src/main/java/IPerson.java").writelns(
                "interface IPerson {",
                "    String getName();",
                "}"
        );
    }

    private void writeLongInterface() {
        file("src/main/java/IPerson.java").writelns(
                "interface IPerson {",
                "    String getName();",
                "    String getAddress();",
                "}"
        );
    }

    private void writeTestClass() {
        file("src/main/java/Person.java").writelns(
                "public class Person implements IPerson {",
                "    private final String name = \"never changes\";",
                "    public String getName() {",
                "        return name;\n" +
                "    }",
                "}"
        );
    }
}
