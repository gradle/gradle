package org.gradle.integtests;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TaskExecutionIntegrationTest extends AbstractIntegrationTest {
    public static boolean graphChecked;

    @Before
    public void setUp() {
        graphChecked = false;
    }

    @Test
    public void taskCanAccessTaskGraph() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "import org.gradle.integtests.TaskExecutionIntegrationTest",
                "createTask('a') { task ->",
                "    assertTrue(build.taskGraph.hasTask(':a'))",
                "    assertTrue(build.taskGraph.allTasks.contains(task))",
                "}",
                "build.taskGraph.whenReady { graph ->",
                "    assertTrue(graph.hasTask(':a'))",
                "    assertTrue(graph.allTasks.contains(task('a')))",
                "    TaskExecutionIntegrationTest.graphChecked = true",
                "}"
        );
        usingBuildFile(buildFile).runTasks("a");

        assertTrue(graphChecked);
    }
}
