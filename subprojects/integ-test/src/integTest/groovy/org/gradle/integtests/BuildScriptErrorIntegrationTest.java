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

import org.gradle.integtests.fixtures.AbstractIntegrationTest;
import org.gradle.integtests.fixtures.executer.ExecutionFailure;
import org.gradle.util.TestFile;
import org.junit.Ignore;
import org.junit.Test;

import static org.gradle.util.Matchers.containsLine;

public class BuildScriptErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsProjectEvaluationFailsWithGroovyException() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns("", "createTakk('do-stuff')");
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
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
        failure.assertThatCause(containsLine(String.format("build file '%s': 2: unable to resolve class org.gradle.unknown.Unknown", buildFile)));
    }

    @Test
    public void reportsNestedProjectEvaluationFailsWithRuntimeException() {
        testFile("settings.gradle").write("include 'child'");

        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "evaluationDependsOn 'child'",
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
        failure.assertHasDescription("broken closure");
        failure.assertHasNoCause();
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
