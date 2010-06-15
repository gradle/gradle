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
import org.gradle.util.TestFile;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

public class BuildScriptErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsProjectEvaluationFailsWithGroovyException() {
        ExecutionFailure failure = usingBuildScript("\ncreateTakk('do-stuff')").runWithFailure();

        failure.assertHasFileName("Embedded build file");
        failure.assertHasLineNumber(2);
        failure.assertHasDescription("A problem occurred evaluating root project 'reportsProjectEvaluationFailsWithGroovyException");
        failure.assertHasCause("Could not find method createTakk() for arguments [do-stuff] on root project 'reportsProjectEvaluationFailsWithGroovyException");
    }

    @Test
    public void reportsScriptCompilationException() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
            "// a comment",
            "import org.gradle.unknown.Unknown",
            "new Unknown()");
        ExecutionFailure failure = inTestDirectory().runWithFailure();
        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasLineNumber(2);
        failure.assertHasDescription(String.format("Could not compile build file '%s'.", buildFile));
    }

    @Test
    public void reportsNestedProjectEvaluationFailsWithRuntimeException() {
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
        failure.assertHasDescription("A problem occurred evaluating project ':child'");
        failure.assertHasCause("failure");
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
        failure.assertHasDescription("Execution failed for task ':do-stuff'");
        failure.assertHasCause("Division by zero");
    }

    @Test
    public void reportsTaskActionExecutionFailsWithRuntimeException() {
        File buildFile = testFile("build.gradle").writelns(
                "task brokenClosure << {",
                "    throw new RuntimeException('broken closure')",
                "}");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("brokenClosure").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasLineNumber(2);
        failure.assertHasDescription("Execution failed for task ':brokenClosure'");
        failure.assertHasCause("broken closure");
    }

    @Test
    public void reportsTaskActionExecutionFailsFromJavaWithRuntimeException() {
        File buildFile = testFile("build.gradle").write("task brokenJavaTask(type: org.gradle.integtests.BrokenTask)");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("brokenJavaTask").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasDescription("Execution failed for task ':brokenJavaTask'");
        failure.assertHasCause("broken action");
    }

    @Test
    public void reportsTaskInjectedByOtherProjectFailsWithRuntimeException() {
        testFile("settings.gradle").write("include 'a', 'b'");
        TestFile buildFile = testFile("b/build.gradle");
        buildFile.writelns(
                "project(':a') {",
                "    task a << {",
                "        throw new RuntimeException('broken')",
                "    }",
                "}");

        ExecutionFailure failure = inTestDirectory().withTasks("a").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasLineNumber(3);
        failure.assertHasDescription("Execution failed for task ':a:a");
        failure.assertHasCause("broken");
    }

    @Test
    public void reportsTaskGraphReadyEventFailsWithRuntimeException() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "gradle.taskGraph.whenReady {",
                "throw new RuntimeException('broken closure')",
                "}",
                "task a");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("a").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasLineNumber(2);
        failure.assertHasDescription("Failed to notify task execution graph listener");
        failure.assertHasCause("broken closure");
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
        failure.assertHasDescription("??");
        failure.assertHasCause("broken");
    }
}
