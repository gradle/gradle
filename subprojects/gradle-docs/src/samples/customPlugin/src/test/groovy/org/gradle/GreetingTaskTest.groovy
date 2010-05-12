package org.gradle

import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import static org.junit.Assert.*

class GreetingTaskTest {
    @Test
    public void greetingTaskPrintsGreeting() {
        Project project = ProjectBuilder.builder().build()
        def task = project.task('greeting', type: GreetingTask)

        assertTrue(task instanceof GreetingTask)
        task.greet()
    }
}