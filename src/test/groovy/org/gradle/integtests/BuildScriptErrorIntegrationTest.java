/*
 * Copyright 2008 the original author or authors.
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
import org.junit.Ignore;

import java.io.File;

public class BuildScriptErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsProjectEvaulationFailsWithGroovyException() {
        ExecutionFailure failure = usingBuildScript("\ncreateTakk('do-stuff')").runWithFailure();

        failure.assertHasFileName("Embedded build file");
        failure.assertHasLineNumber(2);
        failure.assertHasContext("A problem occurred evaluating root project 'tmpTest'");
        failure.assertHasDescription("Could not find method createTakk() for arguments [do-stuff] on root project 'tmpTest'.");
    }

    @Test
    public void reportsGroovyCompilationException() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
            "// a comment",
            "import org.gradle.unknown.Unknown",
            "new Unknown()");
        ExecutionFailure failure = inTestDirectory().runWithFailure();
        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasLineNumber(2);
        failure.assertHasContext(String.format("Could not compile build file '%s'.", buildFile));
    }

    @Test
    public void reportsNestedProjectEvaulationFailsWithRuntimeException() {
        testFile("settings.gradle").write("include 'child'");

        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "dependsOn 'child'",
                "task t");

        TestFile childBuildFile = testFile("child/build.gradle");
        childBuildFile.writelns(
                "def broken = { ->",
                "    throw new RuntimeException('failure') }",
                "broken()");
        ExecutionFailure failure = inTestDirectory().withTasks("t").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", childBuildFile));
        failure.assertHasLineNumber(2);
        failure.assertHasContext("A problem occurred evaluating project ':child'");
        failure.assertHasDescription("failure");
    }

    @Test
    public void reportsTaskActionExecutionFailsWithError() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "task('do-stuff').doFirst",
                "{",
                "1/0",
                "}");
        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("do-stuff").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasLineNumber(3);
        failure.assertHasContext("Execution failed for task ':do-stuff'");
        failure.assertHasDescription("/ by zero");
    }

    @Test
    public void reportsTaskActionExecutionFailsWithRuntimeException() {
        File buildFile = getTestBuildFile("task-action-execution-failure.gradle");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("brokenClosure").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasLineNumber(3);
        failure.assertHasContext("Execution failed for task ':brokenClosure'");
        failure.assertHasDescription("broken closure");
    }

    @Test
    public void reportsTaskActionExecutionFailsFromJavaWithRuntimeException() {
        File buildFile = getTestBuildFile("task-action-execution-failure.gradle");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("brokenJavaTask").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasContext("Execution failed for task ':brokenJavaTask'");
        failure.assertHasDescription("broken action");
    }

    @Test
    public void reportsTaskGraphReadyEventFailsWithRuntimeException() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "build.taskGraph.whenReady {",
                "throw new RuntimeException('broken closure')",
                "}",
                "task a");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("a").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasLineNumber(2);
        failure.assertHasContext("Failed to notify task execution graph listener");
        failure.assertHasDescription("broken closure");
    }
    
    @Test @Ignore
    public void reportsTaskDependencyClosureFailsWithRuntimeException() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "task a",
                "a.dependsOn {",
                "throw new RuntimeException('broken')",
                "}");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("a").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasLineNumber(3);
        failure.assertHasContext("Failed to notify task execution graph listener");
        failure.assertHasDescription("broken closure");
    }
}
