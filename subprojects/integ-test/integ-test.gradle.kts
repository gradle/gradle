import gradlebuild.cleanup.WhenNotEmpty

plugins {
    id("gradlebuild.internal.java")
}

dependencies {
    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":native"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":process-services"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":resources"))
    integTestImplementation(project(":persistentCache"))
    integTestImplementation(project(":dependency-management"))
    integTestImplementation(project(":bootstrap"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.jsoup)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.sampleCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(module = "slf4j-simple")
    }

    crossVersionTestImplementation(project(":base-services"))
    crossVersionTestImplementation(project(":core"))
    crossVersionTestImplementation(project(":plugins"))
    crossVersionTestImplementation(project(":platformJvm"))
    crossVersionTestImplementation(project(":languageJava"))
    crossVersionTestImplementation(project(":languageGroovy"))
    crossVersionTestImplementation(project(":scala"))
    crossVersionTestImplementation(project(":ear"))
    crossVersionTestImplementation(project(":testingJvm"))
    crossVersionTestImplementation(project(":ide"))
    crossVersionTestImplementation(project(":code-quality"))
    crossVersionTestImplementation(project(":signing"))

    integTestImplementation(testFixtures(project(":core")))
    integTestImplementation(testFixtures(project(":diagnostics")))
    integTestImplementation(testFixtures(project(":platformNative")))
    integTestImplementation(libs.jgit)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
