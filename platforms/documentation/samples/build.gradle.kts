plugins {
    id("gradlebuild.internal.java")
}

description = "Integration tests for our documentation snippets (aka samples)"

dependencies {
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.processServices)
    integTestImplementation(projects.persistentCache)
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.samplesCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
    }
    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.modelCore))

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

testFilesCleanup.reportOnly = true

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
