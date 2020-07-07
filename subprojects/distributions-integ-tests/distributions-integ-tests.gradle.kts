plugins {
    id("gradlebuild.internal.java")
}

dependencies {
    integTestImplementation(project(":internalTesting"))
    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.ant)

    integTestBinDistribution(project(":distributionsFull"))
    integTestAllDistribution(project(":distributionsFull"))
    integTestDocsDistribution(project(":distributionsFull"))
    integTestSrcDistribution(project(":distributionsFull"))

    integTestDistributionRuntimeOnly(project(":distributionsFull"))
}

tasks.forkingIntegTest.configure {
    systemProperty("gradleBuildBranch", moduleIdentity.gradleBuildBranch.get())
    systemProperty("gradleBuildCommitId", moduleIdentity.gradleBuildCommitId.get())
}
