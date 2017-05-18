import build.*

apply {
    plugin("kotlin")
}

dependencies {
    compile(project(":test-fixtures"))
}

val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(customInstallation)
    }
}

withParallelTests()
