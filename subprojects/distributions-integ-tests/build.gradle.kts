plugins {
    id("gradlebuild.internal.java")
}

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
    systemProperty("gradleBuildBranch", moduleIdentity.gradleBuildBranch.get())
    systemProperty("gradleBuildCommitId", moduleIdentity.gradleBuildCommitId.get())
}
