import gradlebuild.integrationtests.tasks.IntegrationTest

plugins {
    id("gradlebuild.integration-tests")
}

tasks.withType<IntegrationTest>().configureEach {
    // See AbstractKotlinIntegrationTest
    "kotlinDslTestsExtraRepo".let {
        systemProperty(it, System.getProperty(it))
    }
}

dependencies {
    integTestRuntimeOnly(project(":kotlin-dsl-plugins")) {
        because("Tests require 'future-plugin-versions.properties' on the test classpath and the embedded executer needs them available")
    }
    integTestLocalRepository(project(":kotlin-dsl-plugins"))
}
