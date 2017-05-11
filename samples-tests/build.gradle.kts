import org.gradle.api.tasks.testing.logging.TestExceptionFormat

apply {
    plugin("kotlin")
}

dependencies {
    compile(project(":test-fixtures"))
}

val customInstallation by rootProject.tasks
val test by tasks
test.dependsOn(customInstallation)

tasks.withType<Test> {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        maxParallelForks = gradle.startParameter.maxWorkerCount / 2 + 1
    }
}
