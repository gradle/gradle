package org.gradle.integtests;

import org.junit.Test;

public class TaskExecutionIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void taskCanAccessTaskGraph() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.write("createTask('a') { build.taskExecutionGraph.hasTask(':a') } ");
        usingBuildFile(buildFile).runTasks("a");
    }
}
