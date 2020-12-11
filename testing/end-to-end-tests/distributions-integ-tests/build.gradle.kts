plugins {
    id("gradlebuild.internal.java")
}

dependencies {
    integTestImplementation("org.gradle:internal-testing")
    integTestImplementation("org.gradle:base-services")
    integTestImplementation("org.gradle:logging")
    integTestImplementation("org.gradle:core-api")
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.ant)

    integTestBinDistribution("org.gradle:distributions-full")
    integTestAllDistribution("org.gradle:distributions-full")
    integTestDocsDistribution("org.gradle:distributions-full")
    integTestSrcDistribution("org.gradle:distributions-full")

    integTestDistributionRuntimeOnly("org.gradle:distributions-full")
}

tasks.forkingIntegTest {
    systemProperty("gradleBuildBranch", moduleIdentity.gradleBuildBranch.get())
    systemProperty("gradleBuildCommitId", moduleIdentity.gradleBuildCommitId.get())
}
