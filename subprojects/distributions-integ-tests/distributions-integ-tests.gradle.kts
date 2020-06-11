plugins {
    gradlebuild.internal.java
}

dependencies {
    integTestImplementation(project(":internalTesting"))
    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(library("guava"))
    integTestImplementation(library("commons_io"))
    integTestImplementation(library("ant"))

    integTestBinDistribution(project(":distributionsFull"))
    integTestAllDistribution(project(":distributionsFull"))
    integTestDocsDistribution(project(":distributionsFull"))
    integTestSrcDistribution(project(":distributionsFull"))

    integTestDistributionRuntimeOnly(project(":distributionsFull"))
}

tasks.forkingIntegTest.configure {
    systemProperty("gradleBuildBranch", gitInfo.gradleBuildBranch.get())
    systemProperty("gradleBuildCommitId", gitInfo.gradleBuildCommitId.get())
}
