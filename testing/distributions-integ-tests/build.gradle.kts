import gradlebuild.basics.buildBranch
import gradlebuild.basics.buildCommitId

plugins {
    id("gradlebuild.internal.java")
}

description = "The collector project for the 'integ-tests' portion of the Gradle distribution"

dependencies {
    integTestImplementation(projects.internalTesting)
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.coreApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.ant)

    integTestBinDistribution(projects.distributionsFull)
    integTestAllDistribution(projects.distributionsFull)
    integTestDocsDistribution(projects.distributionsFull)
    integTestSrcDistribution(projects.distributionsFull)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

tasks.forkingIntegTest {
    systemProperty("gradleBuildBranch", buildBranch.get())
    systemProperty("gradleBuildCommitId", buildCommitId.get())
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
