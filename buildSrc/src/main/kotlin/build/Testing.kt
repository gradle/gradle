package build

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat


fun Project.withParallelTests() {
    tasks.withType(Test::class.java) { test ->
        test.testLogging { logging ->
            logging.events("failed")
            logging.exceptionFormat = TestExceptionFormat.FULL
        }
        val maxWorkerCount = gradle.startParameter.maxWorkerCount
        test.maxParallelForks = if (maxWorkerCount < 2) 1 else maxWorkerCount / 2
        test.logger.info("${test.path} will run with maxParallelForks=${test.maxParallelForks}.")
    }
}

