/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test

class TaskErrorExecutionIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsTaskActionExecutionFailsWithError() {
        TestFile buildFile = testFile("build.gradle")
        buildFile.writelns(
                "task('do-stuff').doFirst",
                "{",
                "throw new ArithmeticException('broken')",
                "}")
        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("do-stuff").runWithFailure()

        failure.assertHasFileName(String.format("Build file '%s'", buildFile))
        failure.assertHasLineNumber(3)
        failure.assertHasDescription("Execution failed for task ':do-stuff'.")
        failure.assertHasCause("broken")
    }

    @Test
    public void reportsTaskActionExecutionFailsWithRuntimeException() {
        File buildFile = testFile("build.gradle").writelns(
                "task brokenClosure << {",
                "    throw new RuntimeException('broken closure')",
                "}")

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("brokenClosure").runWithFailure()

        failure.assertHasFileName(String.format("Build file '%s'", buildFile))
        failure.assertHasLineNumber(2)
        failure.assertHasDescription("Execution failed for task ':brokenClosure'.")
        failure.assertHasCause("broken closure")
    }

    @Test
    public void reportsTaskActionExecutionFailsFromJavaWithRuntimeException() {
        testFile("buildSrc/src/main/java/org/gradle/BrokenTask.java").writelns(
                "package org.gradle;",
                "import org.gradle.api.Action;",
                "import org.gradle.api.DefaultTask;",
                "import org.gradle.api.Task;",
                "public class BrokenTask extends DefaultTask {",
                "    public BrokenTask() {",
                "        doFirst(new Action<Task>() {",
                "            public void execute(Task task) {",
                "                throw new RuntimeException(\"broken action\");",
                "            }",
                "        });",
                "    }",
                "}"
        )
        File buildFile = testFile("build.gradle")
        buildFile.write("task brokenJavaTask(type: org.gradle.BrokenTask)")

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("brokenJavaTask").runWithFailure()

        failure.assertHasDescription("Execution failed for task ':brokenJavaTask'.")
        failure.assertHasCause("broken action")
    }

    @Test
    public void reportsTaskInjectedByOtherProjectFailsWithRuntimeException() {
        testFile("settings.gradle").write("include 'a', 'b'")
        TestFile buildFile = testFile("b/build.gradle")
        buildFile.writelns(
                "project(':a') {",
                "    task a << {",
                "        throw new RuntimeException('broken')",
                "    }",
                "}")

        ExecutionFailure failure = inTestDirectory().withTasks("a").runWithFailure()

        failure.assertHasFileName(String.format("Build file '%s'", buildFile))
        failure.assertHasLineNumber(3)
        failure.assertHasDescription("Execution failed for task ':a:a'.")
        failure.assertHasCause("broken")
    }

    @Test
    public void reportsTaskValidationFailure() {
        def buildFile = testFile('build.gradle')
        buildFile << '''
class CustomTask extends DefaultTask {
    @InputFile File srcFile
    @OutputFile File destFile
}

task custom(type: CustomTask)
'''

        ExecutionFailure failure = inTestDirectory().withTasks("custom").runWithFailure()

        failure.assertHasDescription("Some problems were found with the configuration of task ':custom'.")
        failure.assertHasCause("No value has been specified for property 'srcFile'.")
        failure.assertHasCause("No value has been specified for property 'destFile'.")
    }
}
