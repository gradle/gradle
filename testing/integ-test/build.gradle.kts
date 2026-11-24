plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.cross-version-tests")
}

description = "Integration tests which don't fit anywhere else - should probably be split up"

dependencies {
    integTestImplementation(projects.gradleCliMain)
    integTestImplementation(projects.idePlugins)
    integTestImplementation(projects.launcher)
    integTestImplementation(libs.jsoup)
    integTestImplementation(testLibs.samplesCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
    }
    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.modelReflect))
    integTestImplementation(testFixtures(projects.platformNative))
    integTestImplementation(testFixtures(projects.scala))
    integTestImplementation(testFixtures(projects.testingBase))

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
}

testFilesCleanup.reportOnly = true


errorprone {
    nullawayEnabled = true
}
