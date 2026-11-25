plugins {
    id("gradlebuild.internal.java")
}

description = "Integration tests for our documentation snippets (aka samples)"

dependencies {
    integTestImplementation(testLibs.samplesCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
    }
    integTestImplementation(testFixtures(projects.modelCore))
    integTestImplementation(testFixtures(projects.testingBase))

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

testFilesCleanup.reportOnly = true

errorprone {
    nullawayEnabled = true
}
