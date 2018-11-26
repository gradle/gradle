package build

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

import org.gradle.kotlin.dsl.withType


fun Project.withParallelTests() {
    tasks.withType<Test>().configureEach {
        val maxWorkerCount = gradle.startParameter.maxWorkerCount
        maxParallelForks = if (maxWorkerCount < 2) 1 else maxWorkerCount / 2
        logger.info("$path will run with maxParallelForks=$maxParallelForks.")
    }
}
