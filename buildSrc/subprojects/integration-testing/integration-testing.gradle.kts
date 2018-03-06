plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    compile(project(":testing"))
    compile(project(":kotlinDsl"))
    compile(project(":cleanup"))
    testCompile("junit:junit:4.12")
}

gradlePlugin {
    (plugins) {
        "distributionTesting" {
            id = "distribution-testing"
            implementationClass = "org.gradle.gradlebuild.integrationtesting.DistributionTestingPlugin"
        }
    }
}
