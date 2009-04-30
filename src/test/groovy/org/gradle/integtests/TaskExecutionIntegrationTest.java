package org.gradle.integtests;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

public class TaskExecutionIntegrationTest extends AbstractIntegrationTest {
    public static boolean graphListenerNotified;

    @Before
    public void setUp() {
        graphListenerNotified = false;
    }

    @Test
    public void canDefineTasksUsingTaskKeyword() {
        testFile("build.gradle").writelns(
                "task withAction { }",
                "task nothing",
                "2.times { task \"dynamic$it\" {} }",
                "task task {}",
                "if (task) { task inBlock }",
                "def task() { task inMethod }",
                "task()",
                "def cl = { -> task inClosure }",
                "cl()",
                "Task taskVar =  task(inVar)",
                "task withMap(dependsOn: [withAction, nothing, dynamic0, dynamic1, task, inBlock, inMethod, inClosure, taskVar])",
                "task withMapAndAction(dependsOn: withMap) { }"
        );
        inTestDirectory().withTasks("withMapAndAction").run().assertTasksExecuted(":dynamic0", ":dynamic1", ":inBlock",
                ":inClosure", ":inMethod", ":inVar", ":nothing", ":task", ":withAction", ":withMap",
                ":withMapAndAction");
    }
    
    @Test
    public void taskCanAccessTaskGraph() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "import org.gradle.integtests.TaskExecutionIntegrationTest",
                "task a(dependsOn: 'b') { task ->",
                "    assertTrue(build.taskGraph.hasTask(task))",
                "    assertTrue(build.taskGraph.hasTask(':a'))",
                "    assertTrue(build.taskGraph.hasTask(a))",
                "    assertTrue(build.taskGraph.hasTask(':b'))",
                "    assertTrue(build.taskGraph.hasTask(b))",
                "    assertTrue(build.taskGraph.allTasks.contains(task))",
                "    assertTrue(build.taskGraph.allTasks.contains(project.task('b')))",
                "}",
                "task b",
                "build.taskGraph.whenReady { graph ->",
                "    assertTrue(graph.hasTask(':a'))",
                "    assertTrue(graph.hasTask(a))",
                "    assertTrue(graph.hasTask(':b'))",
                "    assertTrue(graph.hasTask(b))",
                "    assertTrue(graph.allTasks.contains(a))",
                "    assertTrue(graph.allTasks.contains(b))",
                "    TaskExecutionIntegrationTest.graphListenerNotified = true",
                "}"
        );
        usingBuildFile(buildFile).withTasks("a").run().assertTasksExecuted(":b", ":a");

        assertTrue(graphListenerNotified);
    }

    @Test
    public void executesAllTasksInASingleBuildAndEachTaskAtMostOnce() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "build.taskGraph.whenReady { assertFalse(project.hasProperty('graphReady')); graphReady = true }",
                "task a { task -> project.executedA = task }",
                "task b { ",
                "    assertSame(a, project.executedA);",
                "    assertTrue(build.taskGraph.hasTask(':a'))",
                "}",
                "task c(dependsOn: a)",
                "task d(dependsOn: a)",
                "task e(dependsOn: [a, d])"
                );
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
                "}"
                );
        usingBuildFile(buildFile).withTasks("a", "c").run().assertTasksExecuted(":a", ":b", ":c", ":child1:b", ":child1:c", ":child2:b", ":child2:c");
        usingBuildFile(buildFile).withTasks("b", ":child2:c").run().assertTasksExecuted(":b", ":child1:b", ":child2:b", ":a", ":child2:c");
    }

    @Test
    public void executesMultiProjectDefaultTasksInASingleBuildAndEachTaskAtMostOnce() {
        testFile("settings.gradle").writelns("include 'child1', 'child2'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "defaultTasks 'a', 'b'",
                "task a",
                "subprojects {",
                "    task a(dependsOn: ':a')",
                "    task b(dependsOn: ':a')",
                "}"
                );
        usingBuildFile(buildFile).run().assertTasksExecuted(":a", ":child1:a", ":child2:a", ":child1:b", ":child2:b");
    }
}
