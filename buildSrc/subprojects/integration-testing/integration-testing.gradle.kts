plugins {
    `java-gradle-plugin`
}

apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    implementation(project(":cleanup"))
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":versioning"))
    implementation(project(":build"))
    implementation(project(":packaging"))
    testCompile("junit:junit:4.12")
}

gradlePlugin {
    (plugins) {
        "crossVersionTests" {
            id = "gradlebuild.cross-version-tests"
            implementationClass = "org.gradle.gradlebuild.test.integrationtests.CrossVersionTestsPlugin"
        }
        "distributionTesting" {
            id = "gradlebuild.distribution-testing"
            implementationClass = "org.gradle.gradlebuild.test.integrationtests.DistributionTestingPlugin"
        }
        "integrationTests" {
            id = "gradlebuild.integration-tests"
            implementationClass = "org.gradle.gradlebuild.test.integrationtests.IntegrationTestsPlugin"
        }
        "intTestImage" {
            id = "gradlebuild.int-test-image"
            implementationClass = "org.gradle.gradlebuild.test.fixtures.IntTestImagePlugin"
        }
        "testFixtures" {
            id = "gradlebuild.test-fixtures"
            implementationClass = "org.gradle.gradlebuild.test.fixtures.TestFixturesPlugin"
        }
    }
}
