dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
}

gradlePlugin {
    plugins {
        register("cleanup") {
            id = "gradlebuild.cleanup"
            implementationClass = "org.gradle.gradlebuild.testing.integrationtests.cleanup.CleanupPlugin"
        }
    }
}
