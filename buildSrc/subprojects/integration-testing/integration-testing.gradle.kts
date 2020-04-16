dependencies {
    api(kotlin("stdlib"))
    implementation(project(":build"))
    implementation(project(":cleanup"))
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":packaging"))
    implementation(project(":versioning"))
    testImplementation("junit:junit:4.12")
}

gradlePlugin {
    plugins {
        register("crossVersionTests") {
            id = "gradlebuild.cross-version-tests"
            implementationClass = "org.gradle.gradlebuild.test.integrationtests.CrossVersionTestsPlugin"
        }
        register("distributionTesting") {
            id = "gradlebuild.distribution-testing"
            implementationClass = "org.gradle.gradlebuild.test.integrationtests.DistributionTestingPlugin"
        }
        register("integrationTests") {
            id = "gradlebuild.integration-tests"
            implementationClass = "org.gradle.gradlebuild.test.integrationtests.IntegrationTestsPlugin"
        }
        register("gradleDistribution") {
            id = "gradlebuild.gradle-distribution"
            implementationClass = "org.gradle.gradlebuild.test.fixtures.GradleDistributionPlugin"
        }
        register("testFixtures") {
            id = "gradlebuild.test-fixtures"
            implementationClass = "org.gradle.gradlebuild.test.fixtures.TestFixturesPlugin"
        }
    }
}
