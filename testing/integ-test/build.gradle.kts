plugins {
    id("gradlebuild.internal.java")
}

description = "Integration tests which don't fit anywhere else - should probably be split up"

dependencies {
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.buildOption)
    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.dependencyManagement)
    integTestImplementation(projects.enterpriseOperations)
    integTestImplementation(projects.gradleCliMain)
    integTestImplementation(projects.idePlugins)
    integTestImplementation(projects.launcher)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.native)
    integTestImplementation(projects.persistentCache)
    integTestImplementation(projects.processServices)
    integTestImplementation(projects.resources)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.jgit)
    integTestImplementation(libs.jsoup)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(testLibs.samplesCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
    }
    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.modelReflect))
    integTestImplementation(testFixtures(projects.platformNative))
    integTestImplementation(testFixtures(projects.scala))

    integTestDistributionRuntimeOnly(projects.distributionsFull)

    crossVersionTestImplementation(projects.baseServices)
    crossVersionTestImplementation(projects.codeQuality)
    crossVersionTestImplementation(projects.core)
    crossVersionTestImplementation(projects.ear)
    crossVersionTestImplementation(projects.functional)
    crossVersionTestImplementation(projects.ide)
    crossVersionTestImplementation(projects.idePlugins)
    crossVersionTestImplementation(projects.internalIntegTesting)
    crossVersionTestImplementation(projects.languageGroovy)
    crossVersionTestImplementation(projects.languageJava)
    crossVersionTestImplementation(projects.languageJvm)
    crossVersionTestImplementation(projects.logging)
    crossVersionTestImplementation(projects.platformJvm)
    crossVersionTestImplementation(projects.pluginsApplication)
    crossVersionTestImplementation(projects.scala)
    crossVersionTestImplementation(projects.signing)
    crossVersionTestImplementation(projects.testingJvm)
    crossVersionTestImplementation(projects.war)

    crossVersionTestDistributionRuntimeOnly(projects.distributionsFull)
}

testFilesCleanup.reportOnly = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}

errorprone {
    nullawayEnabled = true
}
