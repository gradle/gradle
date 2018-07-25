plugins {
    `java-gradle-plugin`
}

apply(plugin = "org.gradle.kotlin.kotlin-dsl")

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
        "testFilesCleanUp" {
            id = "gradlebuild.test-files-cleanup"
            implementationClass = "org.gradle.gradlebuild.testing.integrationtests.cleanup.TestFilesCleanUpPlugin"
        }
    }
}

