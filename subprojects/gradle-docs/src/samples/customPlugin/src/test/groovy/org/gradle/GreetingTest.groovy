package org.gradle

import org.junit.Test
import org.gradle.api.test.ProjectBuilder
import org.gradle.api.Project
import static org.junit.Assert.*

class GreetingTest {
    @Test
    public void greetingTaskPrintsGreeting() {
        Project project = ProjectBuilder.withProjectDir(new File('build/test')).create()
        def task = project.task('greeting', type: Greeting.class)

        assertTrue(task instanceof Greeting)
    }
}