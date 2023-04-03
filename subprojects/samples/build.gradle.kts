plugins {
    id("gradlebuild.internal.java")
}

description = "Integration tests for our documentation snippets (aka samples)"

dependencies {
    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(project(":process-services"))
    integTestImplementation(project(":persistent-cache"))
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.samplesCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(module = "slf4j-simple")
    }
    integTestImplementation(testFixtures(project(":core")))
    integTestImplementation(testFixtures(project(":model-core")))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

testFilesCleanup.reportOnly = true

