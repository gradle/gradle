import build.*

apply {
    plugin("kotlin")
}

dependencies {
    val compile by configurations
    compile(project(":test-fixtures"))
}

val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(customInstallation)
        inputs.dir("../samples")
    }
}

withParallelTests()
