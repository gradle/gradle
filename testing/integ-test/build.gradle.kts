plugins {
    id("gradlebuild.internal.java")
}

description = "Integration tests which don't fit anywhere else - should probably be split up"

dependencies {
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.buildOption)
    integTestImplementation(projects.enterpriseOperations)
    integTestImplementation(projects.native)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.processServices)
    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.resources)
    integTestImplementation(projects.persistentCache)
    integTestImplementation(projects.dependencyManagement)
    integTestImplementation(projects.gradleCliMain)
    integTestImplementation(projects.launcher)
    integTestImplementation(projects.idePlugins)
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.jsoup)

    integTestImplementation(libs.samplesCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
    }
    integTestImplementation(testFixtures(projects.modelCore))

    crossVersionTestImplementation(projects.baseServices)
    crossVersionTestImplementation(projects.core)
    crossVersionTestImplementation(projects.pluginsApplication)
    crossVersionTestImplementation(projects.platformJvm)
    crossVersionTestImplementation(projects.languageJvm)
    crossVersionTestImplementation(projects.languageJava)
    crossVersionTestImplementation(projects.languageGroovy)
    crossVersionTestImplementation(projects.logging)
    crossVersionTestImplementation(projects.scala)
    crossVersionTestImplementation(projects.ear)
    crossVersionTestImplementation(projects.war)
    crossVersionTestImplementation(projects.testingJvm)
    crossVersionTestImplementation(projects.ide)
    crossVersionTestImplementation(projects.idePlugins)
    crossVersionTestImplementation(projects.codeQuality)
    crossVersionTestImplementation(projects.signing)
    crossVersionTestImplementation(projects.functional)

    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.diagnostics))
    integTestImplementation(testFixtures(projects.platformNative))
    integTestImplementation(libs.jgit)
    integTestImplementation(libs.javaParser) {
        because("The Groovy compiler inspects the dependencies at compile time")
    }

    integTestDistributionRuntimeOnly(projects.distributionsFull)
    crossVersionTestDistributionRuntimeOnly(projects.distributionsFull)
}

testFilesCleanup.reportOnly = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
