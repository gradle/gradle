plugins {
    id("gradlebuild.internal.java")
}

dependencies {
    integTestImplementation(project(":internalTesting"))
    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.ant)

    integTestBinDistribution(project(":distributions-full"))
    integTestAllDistribution(project(":distributions-full"))
    integTestDocsDistribution(project(":distributions-full"))
    integTestSrcDistribution(project(":distributions-full"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

tasks.forkingIntegTest.configure {
    systemProperty("gradleBuildBranch", moduleIdentity.gradleBuildBranch.get())
    systemProperty("gradleBuildCommitId", moduleIdentity.gradleBuildCommitId.get())
}
