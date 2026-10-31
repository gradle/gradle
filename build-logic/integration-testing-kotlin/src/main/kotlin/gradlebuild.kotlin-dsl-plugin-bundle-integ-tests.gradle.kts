import gradlebuild.integrationtests.tasks.IntegrationTest

plugins {
    id("gradlebuild.integration-tests")
}

tasks.withType<IntegrationTest>().configureEach {
    // See AbstractKotlinIntegrationTest
    "kotlinDslTestsExtraRepo".let { propName ->
        System.getProperty(propName)?.let { systemProperty(propName, it) }
    }
}

useLocallyBuiltKotlinDslPlugins("integTest")
pluginManager.withPlugin("gradlebuild.cross-version-tests") {
    useLocallyBuiltKotlinDslPlugins("crossVersionTest")
}

fun useLocallyBuiltKotlinDslPlugins(testSourceSet: String) {
    dependencies {
        "${testSourceSet}LocalRepository"(project(":kotlin-dsl-plugins"))
    }
}
