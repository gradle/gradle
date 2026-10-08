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
        "${testSourceSet}RuntimeOnly"(project(":kotlin-dsl-plugins")) {
            because("Tests require 'future-plugin-versions.properties' on the test classpath and the embedded executer needs them available")
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("future-versions-resource"))
            }
        }
        "${testSourceSet}LocalRepository"(project(":kotlin-dsl-plugins"))
    }
}
