plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl")}

dependencies {
    implementation(project(":configuration"))
    implementation(project(":testing"))
    implementation(project(":kotlinDsl"))
}

gradlePlugin {
    (plugins) {
        "cleanup" {
            id = "cleanup"
            implementationClass = "org.gradle.gradlebuild.testing.integrationtests.cleanup.CleanupPlugin"
        }
    }
}

