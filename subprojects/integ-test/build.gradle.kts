plugins {
    id("gradlebuild.internal.java")
}

description = "Integration tests which don't fit anywhere else - should probably be split up"

dependencies {
    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":enterprise-operations"))
    integTestImplementation(project(":native"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":process-services"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(project(":resources"))
    integTestImplementation(project(":persistent-cache"))
    integTestImplementation(project(":dependency-management"))
    integTestImplementation(project(":bootstrap"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":ide-plugins"))
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.jsoup)

    integTestImplementation(libs.samplesCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(module = "slf4j-simple")
    }
    integTestImplementation(testFixtures(project(":model-core")))

    crossVersionTestImplementation(project(":base-services"))
    crossVersionTestImplementation(project(":core"))
    crossVersionTestImplementation(project(":plugins"))
    crossVersionTestImplementation(project(":platform-jvm"))
    crossVersionTestImplementation(project(":language-java"))
    crossVersionTestImplementation(project(":language-groovy"))
    crossVersionTestImplementation(project(":logging"))
    crossVersionTestImplementation(project(":scala"))
    crossVersionTestImplementation(project(":ear"))
    crossVersionTestImplementation(project(":war"))
    crossVersionTestImplementation(project(":testing-jvm"))
    crossVersionTestImplementation(project(":ide"))
    crossVersionTestImplementation(project(":ide-plugins"))
    crossVersionTestImplementation(project(":code-quality"))
    crossVersionTestImplementation(project(":signing"))
    crossVersionTestImplementation(project(":functional"))

    integTestImplementation(testFixtures(project(":core")))
    integTestImplementation(testFixtures(project(":diagnostics")))
    integTestImplementation(testFixtures(project(":platform-native")))
    integTestImplementation(libs.jgit)
    integTestImplementation(libs.javaParser) {
        because("The Groovy compiler inspects the dependencies at compile time")
    }

    integTestDistributionRuntimeOnly(project(":distributions-full"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
}

testFilesCleanup.reportOnly = true
