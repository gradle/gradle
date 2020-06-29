dependencies {
    implementation(project(":basics"))
    implementation(project(":moduleIdentity"))
}

gradlePlugin {
    plugins {
        register("cleanup") {
            id = "gradlebuild.cleanup"
            implementationClass = "org.gradle.gradlebuild.testing.integrationtests.cleanup.CleanupPlugin"
        }
    }
}
