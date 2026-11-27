import gradlebuild.basics.buildBranch
import gradlebuild.basics.buildCommitId

plugins {
    id("gradlebuild.internal.java")
}

description = "The collector project for the 'integ-tests' portion of the Gradle distribution"

dependencies {
    integTestBinDistribution(projects.distributionsFull)
    integTestAllDistribution(projects.distributionsFull)
    integTestDocsDistribution(projects.distributionsFull)
    integTestSrcDistribution(projects.distributionsFull)

    integTestDistributionRuntimeOnly(projects.distributionsFull)

    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.core)
    integTestImplementation(projects.loggingApi)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.groovy)
    integTestImplementation(testLibs.hamcrest)
    integTestImplementation(testLibs.spock)
}

// Using lazy makes sure we do not invalidate CC entries when head commit changes
// The hack is needed because Gradle does not support `Provider<?>` in systemProperty
// See https://github.com/gradle/gradle/issues/12247
tasks.forkingIntegTest {
    systemProperty("gradleBuildBranch", lazy(buildBranch::get))
    systemProperty("gradleBuildCommitId", lazy(buildCommitId::get))
}


errorprone {
    nullawayEnabled = true
}
