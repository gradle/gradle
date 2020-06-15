import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

plugins {
    gradlebuild.internal.java
}

dependencies {
    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":native"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":processServices"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":resources"))
    integTestImplementation(project(":persistentCache"))
    integTestImplementation(project(":dependencyManagement"))
    integTestImplementation(project(":bootstrap"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(library("groovy"))
    integTestImplementation(library("slf4j_api"))
    integTestImplementation(library("guava"))
    integTestImplementation(library("ant"))
    integTestImplementation(testLibrary("jsoup"))
    integTestImplementation(testLibrary("jetty"))
    integTestImplementation(testLibrary("sampleCheck")) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(module = "slf4j-simple")
    }

    crossVersionTestImplementation(project(":baseServices"))
    crossVersionTestImplementation(project(":core"))
    crossVersionTestImplementation(project(":plugins"))
    crossVersionTestImplementation(project(":platformJvm"))
    crossVersionTestImplementation(project(":languageJava"))
    crossVersionTestImplementation(project(":languageGroovy"))
    crossVersionTestImplementation(project(":scala"))
    crossVersionTestImplementation(project(":ear"))
    crossVersionTestImplementation(project(":testingJvm"))
    crossVersionTestImplementation(project(":ide"))
    crossVersionTestImplementation(project(":codeQuality"))
    crossVersionTestImplementation(project(":signing"))

    integTestImplementation(testFixtures(project(":core")))
    integTestImplementation(testFixtures(project(":diagnostics")))
    integTestImplementation(testFixtures(project(":platformNative")))
    integTestImplementation(library("jgit"))

    integTestDistributionRuntimeOnly(project(":distributionsFull"))
    crossVersionTestDistributionRuntimeOnly(project(":distributionsFull"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
