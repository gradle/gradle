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

import org.gradle.integtests.fixtures.AbstractIntegrationTest;
import org.gradle.test.fixtures.file.TestFile;
import org.junit.Test;
import spock.lang.Issue;

import static org.hamcrest.Matchers.startsWith;

public class TaskExecutionIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void taskCanAccessTaskGraph() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "boolean notified = false",
                "task a(dependsOn: 'b') << { task ->",
                "    assert notified",
                "    assert gradle.taskGraph.hasTask(task)",
                "    assert gradle.taskGraph.hasTask(':a')",
                "    assert gradle.taskGraph.hasTask(a)",
                "    assert gradle.taskGraph.hasTask(':b')",
                "    assert gradle.taskGraph.hasTask(b)",
                "    assert gradle.taskGraph.allTasks.contains(task)",
                "    assert gradle.taskGraph.allTasks.contains(tasks.getByName('b'))",
                "}",
                "task b",
                "gradle.taskGraph.whenReady { graph ->",
                "    assert graph.hasTask(':a')",
                "    assert graph.hasTask(a)",
                "    assert graph.hasTask(':b')",
                "    assert graph.hasTask(b)",
                "    assert graph.allTasks.contains(a)",
                "    assert graph.allTasks.contains(b)",
                "    notified = true",
                "}");
        usingBuildFile(buildFile).withTasks("a").run().assertTasksExecuted(":b", ":a");
    }

    @Test
    public void executesAllTasksInASingleBuildAndEachTaskAtMostOnce() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "gradle.taskGraph.whenReady { assert !project.hasProperty('graphReady'); ext.graphReady = true }",
                "task a << { task -> project.ext.executedA = task }",
                "task b << { ",
                "    assert a == project.executedA",
                "    assert gradle.taskGraph.hasTask(':a')",
                "}",
                "task c(dependsOn: a)",
                "task d(dependsOn: a)",
                "task e(dependsOn: [a, d])");
        usingBuildFile(buildFile).withTasks("a", "b").run().assertTasksExecuted(":a", ":b");
        usingBuildFile(buildFile).withTasks("a", "a").run().assertTasksExecuted(":a");
        usingBuildFile(buildFile).withTasks("c", "a").run().assertTasksExecuted(":a", ":c");
        usingBuildFile(buildFile).withTasks("c", "e").run().assertTasksExecuted(":a", ":c", ":d", ":e");
    }

    @Test
    public void executesMultiProjectsTasksInASingleBuildAndEachTaskAtMostOnce() {
        testFile("settings.gradle").writelns("include 'child1', 'child2'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "task a",
                "allprojects {",
                "    task b",
                "    task c(dependsOn: ['b', ':a'])",
                "}");
        usingBuildFile(buildFile).withTasks("a", "c").run().assertTasksExecuted(":a", ":b", ":c", ":child1:b",
                ":child1:c", ":child2:b", ":child2:c");
        usingBuildFile(buildFile).withTasks("b", ":child2:c").run().assertTasksExecuted(":b", ":child1:b", ":child2:b",
                ":a", ":child2:c");
    }

    @Test
    public void executesMultiProjectDefaultTasksInASingleBuildAndEachTaskAtMostOnce() {
        testFile("settings.gradle").writelns("include 'child1', 'child2'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns("defaultTasks 'a', 'b'", "task a", "subprojects {", "    task a(dependsOn: ':a')",
                "    task b(dependsOn: ':a')", "}");
        usingBuildFile(buildFile).run().assertTasksExecuted(":a", ":child1:a", ":child2:a", ":child1:b", ":child2:b");
    }

    @Test
    public void executesProjectDefaultTasksWhenNoneSpecified() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "task a",
                "task b(dependsOn: a)",
                "defaultTasks 'b'"
        );
        usingBuildFile(buildFile).withTasks().run().assertTasksExecuted(":a", ":b");
    }
    
    @Test
    public void doesNotExecuteTaskActionsWhenDryRunSpecified() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "task a << { fail() }",
                "task b(dependsOn: a) << { fail() }",
                "defaultTasks 'b'"
        );

        // project defaults
        usingBuildFile(buildFile).withArguments("-m").run().assertTasksExecuted(":a", ":b");
        // named tasks
        usingBuildFile(buildFile).withArguments("-m").withTasks("b").run().assertTasksExecuted(":a", ":b");
    }

    @Test
    public void executesTaskActionsInCorrectEnvironment() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                // An action attached to built-in task
                "task a << { assert Thread.currentThread().contextClassLoader == getClass().classLoader }",
                // An action defined by a custom task
                "task b(type: CustomTask)",
                "class CustomTask extends DefaultTask { @TaskAction def go() { assert Thread.currentThread().contextClassLoader == getClass().classLoader } } ",
                // An action implementation
                "task c; c.doLast new Action<Task>() { void execute(Task t) { assert Thread.currentThread().contextClassLoader == getClass().classLoader } }"
        );

        usingBuildFile(buildFile).withTasks("a", "b", "c").run();
    }

    @Test
    public void excludesTasksWhenExcludePatternSpecified() {
        testFile("settings.gradle").write("include 'sub'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "task a",
                "task b(dependsOn: a)",
                "task c(dependsOn: [a, b])",
                "task d(dependsOn: c)",
                "defaultTasks 'd'"
        );
        testFile("sub/build.gradle").writelns(
                "task c",
                "task d(dependsOn: c)"
        );

        // Exclude entire branch
        usingBuildFile(buildFile).withTasks(":d").withArguments("-x", "c").run().assertTasksExecuted(":d");
        // Exclude direct dependency
        usingBuildFile(buildFile).withTasks(":d").withArguments("-x", "b").run().assertTasksExecuted(":a", ":c", ":d");
        // Exclude using paths and multi-project
        usingBuildFile(buildFile).withTasks("d").withArguments("-x", "c").run().assertTasksExecuted(":d", ":sub:d");
        usingBuildFile(buildFile).withTasks("d").withArguments("-x", "sub:c").run().assertTasksExecuted(":a", ":b", ":c", ":d", ":sub:d");
        usingBuildFile(buildFile).withTasks("d").withArguments("-x", ":sub:c").run().assertTasksExecuted(":a", ":b", ":c", ":d", ":sub:d");
        usingBuildFile(buildFile).withTasks("d").withArguments("-x", "d").run().assertTasksExecuted();
        // Project defaults
        usingBuildFile(buildFile).withArguments("-x", "b").run().assertTasksExecuted(":a", ":c", ":d", ":sub:c", ":sub:d");
        // Unknown task
        usingBuildFile(buildFile).withTasks("d").withArguments("-x", "unknown").runWithFailure().assertThatDescription(startsWith("Task 'unknown' not found in root project"));
    }

    @Test
    @Issue("http://issues.gradle.org/browse/GRADLE-2022")
    public void tryingToInstantiateTaskDirectlyFailsWithGoodErrorMessage() {
        usingBuildFile(testFile("build.gradle").write("new DefaultTask()")).
        withTasks("tasks").
        runWithFailure().
        assertHasCause("Task of type 'org.gradle.api.DefaultTask' has been instantiated directly which is not supported");
    }
}
