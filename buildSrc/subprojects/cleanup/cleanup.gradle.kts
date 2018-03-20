plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
}

gradlePlugin {
    (plugins) {
        "cleanup" {
            id = "gradlebuild.cleanup"
            implementationClass = "org.gradle.gradlebuild.testing.integrationtests.cleanup.CleanupPlugin"
        }
    }
}

