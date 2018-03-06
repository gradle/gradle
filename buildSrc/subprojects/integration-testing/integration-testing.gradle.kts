plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    compile(project(":cleanup"))
    compile(project(":configuration"))
    compile(project(":kotlinDsl"))
    compile(project(":testing"))
    compile(project(":versioning"))
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
        "testFixtures" {
            id = "gradlebuild.test-fixtures"
            implementationClass = "org.gradle.gradlebuild.test.fixtures.TestFixturesPlugin"
        }
    }
}
