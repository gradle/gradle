package build

import org.gradle.api.Project
import org.gradle.api.internal.initialization.DefaultClassLoaderScope
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

import org.gradle.kotlin.dsl.withType


fun Project.withTestStrictClassLoading() {
    tasks.withType<Test> {
        systemProperty(DefaultClassLoaderScope.STRICT_MODE_PROPERTY, true)
    }
}


fun Project.withTestWorkersMemoryLimits(min: String = "64m", max: String = "128m") {
    tasks.withType<Test> {
        jvmArgs("-Xms$min", "-Xmx$max")
    }
}


fun Project.withParallelTests() {
    tasks.withType<Test> {
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
        val maxWorkerCount = gradle.startParameter.maxWorkerCount
        maxParallelForks = if (maxWorkerCount < 2) 1 else maxWorkerCount / 2
        logger.info("$path will run with maxParallelForks=$maxParallelForks.")
    }
}
