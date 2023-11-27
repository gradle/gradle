package org.example;

import org.junit.Test;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.api.Project;
import static org.junit.Assert.assertTrue;

public class GreetingPluginTest {
    @Test
    public void greeterPluginAddsGreetingTaskToProject() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("org.example.greeting");

        assertTrue(project.getTasks().getByName("hello") instanceof GreetingTask);
    }
}
