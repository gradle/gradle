plugins {
    id("gradlebuild.integration-tests")
}

dependencies {
    integTestRuntimeOnly(project(":kotlin-dsl-plugins")) {
        isTransitive = false
        because("Tests require 'future-plugin-versions.properties' on the test classpath")
    }
    integTestLocalRepository(project(":kotlin-dsl-plugins"))
}
