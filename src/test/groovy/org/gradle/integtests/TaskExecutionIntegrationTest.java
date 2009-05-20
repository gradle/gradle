package org.gradle.integtests;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TaskExecutionIntegrationTest extends AbstractIntegrationTest {
    public static boolean graphListenerNotified;

    @Before
    public void setUp() {
        graphListenerNotified = false;
    }

    @Test
    public void canDefineTasksUsingTaskKeywordAndIdentifier() {
        testFile("build.gradle").writelns(
                "task nothing",
                "task withAction << { }",
                "task emptyOptions()",
                "task task",
                "task withOptions(dependsOn: [nothing, withAction, emptyOptions, task])",
                "task withOptionsAndAction(dependsOn: withOptions) << { }"
        );
        inTestDirectory().withTasks("withOptionsAndAction").run().assertTasksExecuted(":emptyOptions", ":nothing",
                ":task", ":withAction", ":withOptions", ":withOptionsAndAction");
    }

    @Test
    public void canDefineTasksUsingTaskKeywordAndGString() {
        testFile("build.gradle").writelns(
                "v = 'Task'",
                "task \"nothing$v\"",
                "task \"withAction$v\" << { }",
                "task \"emptyOptions$v\"()",
                "task \"withOptions$v\"(dependsOn: [nothingTask, withActionTask, emptyOptionsTask])",
                "task \"withOptionsAndAction$v\"(dependsOn: withOptionsTask) << { }"
        );
        inTestDirectory().withTasks("withOptionsAndActionTask").run().assertTasksExecuted(":emptyOptionsTask",
                ":nothingTask", ":withActionTask", ":withOptionsTask", ":withOptionsAndActionTask");
    }

    @Test
    public void canDefineTasksUsingTaskKeywordAndString() {
        testFile("build.gradle").writelns(
                "task 'nothing'",
                "task 'withAction' << { }",
                "task 'emptyOptions'()",
                "task 'withOptions'(dependsOn: [nothing, withAction, emptyOptions])",
                "task 'withOptionsAndAction'(dependsOn: withOptions) << { }"
        );
        inTestDirectory().withTasks("withOptionsAndAction").run().assertTasksExecuted(":emptyOptions", ":nothing",
                ":withAction", ":withOptions", ":withOptionsAndAction");
    }

    @Test
    public void canDefineTasksInNestedBlocks() {
        testFile("build.gradle").writelns(
                "2.times { task \"dynamic$it\" << { } }",
                "if (dynamic0) { task inBlock }",
                "def task() { task inMethod }",
                "task()",
                "def cl = { -> task inClosure }",
                "cl()",
                "task all(dependsOn: [dynamic0, dynamic1, inBlock, inMethod, inClosure])"
        );
        inTestDirectory().withTasks("all").run().assertTasksExecuted(":dynamic0", ":dynamic1", ":inBlock", ":inClosure",
                ":inMethod", ":all");
    }

    @Test
    public void canDefineTasksUsingTaskMethodExpression() {
        testFile("build.gradle").writelns(
                "a = 'a' == 'b' ? null: task(withAction) << { }",
                "a = task(nothing)",
                "a = task(emptyOptions())",
                "taskName = 'dynamic'",
                "a = task(\"$taskName\") << { }",
                "a = task('string')",
                "a = task('stringWithAction') << { }",
                "a = task('stringWithOptions', description: 'description')",
                "a = task('stringWithOptionsAndAction', description: 'description') << { }",
                "a = task(withOptions, description: 'description')",
                "a = task(withOptionsAndAction, description: 'description') << { }",
                "a = task(anotherWithAction).doFirst\n{}",
                "task all(dependsOn: tasks.all)"
        );
        inTestDirectory().withTasks("all").run().assertTasksExecuted(":anotherWithAction", ":dynamic", ":emptyOptions",
                ":nothing", ":string", ":stringWithAction", ":stringWithOptions", ":stringWithOptionsAndAction",
                ":withAction", ":withOptions", ":withOptionsAndAction", ":all");
    }

    @Test
    public void canConfigureTasksWhenTheyAreDefined() {
        testFile("build.gradle").writelns(
                "import org.gradle.integtests.TestTask",
                "task withDescription { description = 'value' }",
                "task(asMethod)\n{ description = 'value' }",
                "task asStatement(type: TestTask) { property = 'value' }",
                "task \"dynamic\"(type: TestTask) { property = 'value' }",
                "v = task(asExpression, type: TestTask) { property = 'value' }",
                "task(postConfigure, type: TestTask).configure { property = 'value' }",
                "[asStatement, dynamic, asExpression, postConfigure].each { ",
                "    assertEquals('value', it.property)",
                "}",
                "[withDescription, asMethod].each {",
                "    assertEquals('value', it.description)",
                "}",
                "task all(dependsOn: tasks.all)"
        );
        inTestDirectory().withTasks("all").run();
    }

    @Test
    public void doesNotHideLocalMethodsAndVariables() {
        testFile("build.gradle").writelns(
                "String name = 'a'; task name",
//                "taskNameVar = 'b'; task taskNameVar",
                "def taskNameMethod(String name = 'c') { name } ",
//                "task taskNameMethod",
                "task taskNameMethod('d')",
                "def method(String taskNameParam) { task taskNameParam }",
                "method('e')",
                "cl = { taskNameParam -> task taskNameParam }",
                "cl.call('f')",
                "task all(dependsOn: tasks.all)"
        );
        inTestDirectory().withTasks("all").run().assertTasksExecuted(":a", ":d", ":e", ":f", ":all");
    }

    @Test
    public void taskCanAccessTaskGraph() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "import org.gradle.integtests.TaskExecutionIntegrationTest",
                "task a(dependsOn: 'b') << { task ->",
                "    assertTrue(build.taskGraph.hasTask(task))",
                "    assertTrue(build.taskGraph.hasTask(':a'))",
                "    assertTrue(build.taskGraph.hasTask(a))",
                "    assertTrue(build.taskGraph.hasTask(':b'))",
                "    assertTrue(build.taskGraph.hasTask(b))",
                "    assertTrue(build.taskGraph.allTasks.contains(task))",
                "    assertTrue(build.taskGraph.allTasks.contains(tasks.getByName('b')))",
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
                "task a << { task -> project.executedA = task }",
                "task b << { ",
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
