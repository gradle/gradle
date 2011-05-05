/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest;
import org.gradle.util.TestFile;
import org.junit.Test;

import java.io.IOException;

public class JavaProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void handlesEmptyProject() {
        testFile("build.gradle").writelns("apply plugin: 'java'");
        inTestDirectory().withTasks("build").run();
    }

    @Test
    public void compilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns("apply plugin: 'java'");
        testFile("src/main/java/org/gradle/broken.java").write("broken");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("build").runWithFailure();

        failure.assertHasDescription("Execution failed for task ':compileJava'");
        failure.assertHasCause("Compile failed; see the compiler error output for details.");
    }

    @Test
    public void testCompilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns("apply plugin: 'java'");
        testFile("src/main/java/org/gradle/ok.java").write("package org.gradle; class ok { }");
        testFile("src/test/java/org/gradle/broken.java").write("broken");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("build").runWithFailure();

        failure.assertHasDescription("Execution failed for task ':compileTestJava'");
        failure.assertHasCause("Compile failed; see the compiler error output for details.");
    }

    @Test
    public void handlesTestSrcWhichDoesNotContainAnyTestCases() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns("apply plugin: 'java'");
        testFile("src/test/java/org/gradle/NotATest.java").writelns("package org.gradle;", "public class NotATest {}");

        usingBuildFile(buildFile).withTasks("build").run();
    }

    @Test
    public void javadocGenerationFailureBreaksBuild() throws IOException {
        TestFile buildFile = testFile("javadocs.gradle");
        buildFile.write("apply plugin: 'java'");
        testFile("src/main/java/org/gradle/broken.java").write("class Broken { }");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("javadoc").runWithFailure();

        failure.assertHasDescription("Execution failed for task ':javadoc'");
        failure.assertHasCause("Javadoc generation failed.");
    }

    @Test
    public void handlesResourceOnlyProject() throws IOException {
        TestFile buildFile = testFile("resources.gradle");
        buildFile.write("apply plugin: 'java'");
        testFile("src/main/resources/org/gradle/resource.file").write("test resource");

        usingBuildFile(buildFile).withTasks("build").run();
        testFile("build/resources/main/org/gradle/resource.file").assertExists();
    }

    @Test
    public void separatesOutputResourcesFromCompiledClasses() throws IOException {
        //given
        TestFile buildFile = testFile("build.gradle");
        buildFile.write("apply plugin: 'java'");

        testFile("src/main/resources/prod.resource").write("");
        testFile("src/main/java/Main.java").write("class Main {}");
        testFile("src/test/resources/test.resource").write("test resource");
        testFile("src/test/java/TestFoo.java").write("class TestFoo {}");

        //when
        usingBuildFile(buildFile).withTasks("build").run();

        //then
        testFile("build/resources/main/prod.resource").assertExists();
        testFile("build/classes/main/prod.resource").assertDoesNotExist();

        testFile("build/resources/test/test.resource").assertExists();
        testFile("build/classes/test/test.resource").assertDoesNotExist();

        testFile("build/classes/main/Main.class").assertExists();
        testFile("build/classes/test/TestFoo.class").assertExists();
    }

    @Test
    public void generatesArtifactsWhenVersionIsEmpty() {
        testFile("settings.gradle").write("rootProject.name = 'empty'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "apply plugin: 'java'",
                "version = ''"
        );
        testFile("src/main/resources/org/gradle/resource.file").write("some resource");

        usingBuildFile(buildFile).withTasks("jar").run();
        testFile("build/libs/empty.jar").assertIsFile();
    }
}
