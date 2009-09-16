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

import org.junit.Test;

import java.io.IOException;

public class JavaProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void handlesEmptyProject() {
        testFile("build.gradle").writelns(
                "usePlugin('java')"
        );
        inTestDirectory().withTasks("build").run();
    }

    @Test
    public void compilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "usePlugin('java')"
        );
        testFile("src/main/java/org/gradle/broken.java").write("broken");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("build").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasDescription("Execution failed for task ':compile'");
        failure.assertHasCause("Compile failed; see the compiler error output for details.");
    }

    @Test
    public void testCompilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "usePlugin('java')"
        );
        testFile("src/main/java/org/gradle/ok.java").write("package org.gradle; class ok { }");
        testFile("src/test/java/org/gradle/broken.java").write("broken");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("build").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasDescription("Execution failed for task ':compileTest'");
        failure.assertHasCause("Compile failed; see the compiler error output for details.");
    }

    @Test
    public void handlesTestSrcDoesNotContainAnyTestCases() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "usePlugin('java')"
        );
        testFile("src/test/java/org/gradle/NotATest.java").writelns(
                "package org.gradle;",
                "public class NotATest {}");

        usingBuildFile(buildFile).withTasks("build").run();
    }

    @Test
    public void javadocGenerationFailureBreaksBuild() throws IOException {
        TestFile buildFile = testFile("javadocs.gradle");
        buildFile.write("usePlugin('java')");
        testFile("src/main/java/org/gradle/broken.java").write("broken");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("javadoc").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasDescription("Execution failed for task ':javadoc'");
        failure.assertHasCause("Javadoc generation failed.");
    }

    @Test
    public void handlesResourceOnlyProject() throws IOException {
        TestFile buildFile = testFile("resources.gradle");
        buildFile.write("usePlugin('java')");
        testFile("src/main/resources/org/gradle/resource.file").write("test resource");

        usingBuildFile(buildFile).withTasks("build").run();
        testFile("build/classes/main/org/gradle/resource.file").assertExists();
    }
}
