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
    public void taskCanAccessTaskGraph() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "import org.gradle.integtests.TaskExecutionIntegrationTest",
                "createTask('a', dependsOn: 'b') { task ->",
                "    assertTrue(build.taskGraph.hasTask(':a'))",
                "    assertTrue(build.taskGraph.hasTask(':b'))",
                "    assertTrue(build.taskGraph.allTasks.contains(task))",
                "    assertTrue(build.taskGraph.allTasks.contains(project.task('b')))",
                "}",
                "createTask('b')",
                "build.taskGraph.whenReady { graph ->",
                "    assertTrue(graph.hasTask(':a'))",
                "    assertTrue(graph.hasTask(':b'))",
                "    assertTrue(graph.allTasks.contains(task('a')))",
                "    assertTrue(graph.allTasks.contains(task('b')))",
                "    TaskExecutionIntegrationTest.graphListenerNotified = true",
                "}"
        );
        usingBuildFile(buildFile).runTasks("a").assertTasksExecuted(":b", ":a");

        assertTrue(graphListenerNotified);
    }

    @Test
    public void buildIsReusedForDagNeutralPrimaryTasks() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "createTask('a') { task -> project.executedA = task }",
                "a.dagNeutral = true",
                "createTask('b') { ",
                "    assertSame(a, project.executedA);",
                "    assertTrue(build.taskGraph.hasTask(':a'))",
                "}",
                "createTask('c', dependsOn: 'a')",
                "c.dagNeutral = true"
                );
        usingBuildFile(buildFile).runTasks("a", "b").assertTasksExecuted(":a", ":b");
        // does not execute task more than once
        usingBuildFile(buildFile).runTasks("a", "a").assertTasksExecuted(":a");
        usingBuildFile(buildFile).runTasks("c", "a").assertTasksExecuted(":a", ":c");
    }

    @Test
    public void buildIsReusedForMergedBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "createTask('a') { task -> project.executedA = task }",
                "createTask('b') { ",
                "    assertSame(a, project.executedA);",
                "    assertTrue(build.taskGraph.hasTask(':a'))",
                "}",
                "createTask('c', dependsOn: 'a')"
                );
        usingBuildFile(buildFile).inMergedBuild().runTasks("a", "b").assertTasksExecuted(":a", ":b");
        // does not execute task more than once
        usingBuildFile(buildFile).inMergedBuild().runTasks("a", "a").assertTasksExecuted(":a");
        usingBuildFile(buildFile).inMergedBuild().runTasks("c", "a").assertTasksExecuted(":a", ":c");
    }

    @Test @Ignore
    public void archiveWithImplicitAndExplicitDependencies() {
        testFile("settings.gradle").write("include ('a', 'b')");
        testFile("a/build.gradle").writelns(
                "dependsOn(':b')",
                "createTask('libs', type: org.gradle.api.tasks.bundling.Bundle)",
                // todo - should not have to do these next 2 lines
                "version = 'none'",
                "libs.defaultArchiveTypes = org.gradle.api.plugins.JavaPluginConvention.DEFAULT_ARCHIVE_TYPES",
                "libs {",
                "    zip() {",
                "        destinationDir = file('something.zip')",
                "        files(file('src'))",
                "    }",
                "}",
                "libs.dependsOn('compile')",
                "createTask('compile')"
        );
        testFile("b/build.gradle").writelns(
                "createTask('libs')",
                "createTask('archive_zip')"
        );

        // todo - should archive_zip depend on b:libs, as it is a dependencu of its parent libs task?
        usingBuildFile(testFile("build.gradle")).runTasks("a:archive_zip").assertTasksExecuted(":a:compile", "b:archive_zip", "a:archive_zip");
        
        usingBuildFile(testFile("build.gradle")).runTasks("a:libs").assertTasksExecuted(":a:compile", "b:archive_zip", "b:libs", "a:archive_zip");
    }

}
