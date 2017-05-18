package build

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat


fun Project.withParallelTests() {
    tasks.withType(Test::class.java) {
        it.testLogging {
            it.events("failed")
            it.exceptionFormat = TestExceptionFormat.FULL
        }
        it.maxParallelForks = gradle.startParameter.maxWorkerCount / 2 + 1
    }
}

