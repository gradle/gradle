plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.cross-version-tests")
}

description = "Integration tests which don't fit anywhere else - should probably be split up"

dependencies {
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.buildOperations)
    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.dependencyManagement)
    integTestImplementation(projects.enterpriseOperations)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.native)
    integTestImplementation(projects.problemsApi)
    integTestImplementation(projects.time)
    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.modelReflect))
    integTestImplementation(testFixtures(projects.scala))
    integTestImplementation(testFixtures(projects.testingBase))
    integTestImplementation(libs.groovy)
    integTestImplementation(testLibs.hamcrest)
    integTestImplementation(testLibs.spock)

    integTestDistributionRuntimeOnly(projects.distributionsFull)

    crossVersionTestImplementation(projects.codeQuality)
    crossVersionTestImplementation(projects.ear)
    crossVersionTestImplementation(projects.idePlugins)
    crossVersionTestImplementation(projects.internalIntegTesting)
    crossVersionTestImplementation(projects.languageGroovy)
    crossVersionTestImplementation(projects.pluginsApplication)
    crossVersionTestImplementation(projects.scala)
    crossVersionTestImplementation(projects.signing)
    crossVersionTestImplementation(projects.testingJvm)
    crossVersionTestImplementation(projects.war)

    crossVersionTestDistributionRuntimeOnly(projects.distributionsFull)

    integTestRuntimeOnly(projects.idePlugins)
    integTestRuntimeOnly(projects.launcher)
}

testFilesCleanup.reportOnly = true


errorprone {
    nullawayEnabled = true
}
