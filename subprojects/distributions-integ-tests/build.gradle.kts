import gradlebuild.basics.buildBranch
import gradlebuild.basics.buildCommitId

plugins {
    id("gradlebuild.internal.java")
}

description = "The collector project for the 'integ-tests' portion of the Gradle distribution"

dependencies {
    integTestImplementation(project(":internal-testing"))
    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.ant)

    integTestBinDistribution(project(":distributions-full"))
    integTestAllDistribution(project(":distributions-full"))
    integTestDocsDistribution(project(":distributions-full"))
    integTestSrcDistribution(project(":distributions-full"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

tasks.forkingIntegTest {
    systemProperty("gradleBuildBranch", buildBranch.get())
    systemProperty("gradleBuildCommitId", buildCommitId.get())
}
