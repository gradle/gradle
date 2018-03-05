plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    implementation(project(":build"))
    implementation(project(":kotlinDsl"))
    implementation(project(":configuration"))
    implementation(project(":cleanup"))
}

gradlePlugin {
    (plugins) {
        "distributionTesting" {
            id = "gradlebuild.distribution-testing"
            implementationClass = "org.gradle.testing.DistributionTestingPlugin"
        }
    }
}
