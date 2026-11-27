plugins {
    id("gradlebuild.internal.java")
}

description = "Integration tests for our documentation snippets (aka samples)"

dependencies {
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.jvmServices)
    integTestImplementation(projects.stdlibJavaExtensions)
    integTestImplementation(testFixtures(projects.testingBase))
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.groovyXml)
    integTestImplementation(testLibs.hamcrest)
    integTestImplementation(testLibs.spock)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

testFilesCleanup.reportOnly = true

errorprone {
    nullawayEnabled = true
}
