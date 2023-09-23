import gradlebuild.integrationtests.tasks.IntegrationTest

plugins {
    id("gradlebuild.integration-tests")
}

tasks.withType<IntegrationTest>().configureEach {
    // See AbstractKotlinIntegrationTest
    "kotlinDslTestsExtraRepo".let {
        systemProperty(it, System.getenv(it))
    }
}

dependencies {
    integTestRuntimeOnly(project(":kotlin-dsl-plugins")) {
        isTransitive = false
        because("Tests require 'future-plugin-versions.properties' on the test classpath")
    }
    integTestLocalRepository(project(":kotlin-dsl-plugins"))
}
