import gradlebuild.cleanup.WhenNotEmpty

plugins {
    id("gradlebuild.internal.java")
}

dependencies {
    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(project(":process-services"))
    integTestImplementation(project(":persistent-cache"))
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.sampleCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(module = "slf4j-simple")
    }
    integTestImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

integTest.usesSamples.set(true)
