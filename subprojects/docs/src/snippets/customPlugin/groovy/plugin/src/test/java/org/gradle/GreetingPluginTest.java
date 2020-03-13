package org.gradle;

import org.junit.Test;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.api.Project;
import static org.junit.Assert.assertTrue;

// tag::test-plugin[]
public class GreetingPluginTest {
    @Test
    public void greeterPluginAddsGreetingTaskToProject() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("org.samples.greeting");

        assertTrue(project.getTasks().getByName("hello") instanceof GreetingTask);
    }
}
// end::test-plugin[]
