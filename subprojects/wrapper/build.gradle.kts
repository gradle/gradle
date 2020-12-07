import java.util.jar.Attributes

plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":cli"))

    testImplementation(project(":base-services"))
    testImplementation(project(":native"))
    testImplementation(libs.ant)
    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":logging"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(libs.jetty)

    crossVersionTestImplementation(project(":logging"))
    crossVersionTestImplementation(project(":persistent-cache"))
    crossVersionTestImplementation(project(":launcher"))

    integTestNormalizedDistribution(project(":distributions-full"))
    crossVersionTestNormalizedDistribution(project(":distributions-full"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
}

val executableJar by tasks.registering(Jar::class) {
    archiveFileName.set("gradle-wrapper.jar")
    manifest {
        attributes.remove(Attributes.Name.IMPLEMENTATION_VERSION.toString())
        attributes(Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle Wrapper")
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().incoming.artifactView {
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
    }.files)
}

tasks.jar {
    from(executableJar)
}

// === TODO remove and address the following when we have a good reason to change the wrapper jar
executableJar {
    val cliClasspath = layout.buildDirectory.file("gradle-cli-classpath.properties") // This file was accidentally included into the gradle-wrapper.jar
    val cliParameterNames = layout.buildDirectory.file("gradle-cli-parameter-names.properties")  // This file was accidentally included into the gradle-wrapper.jar
    doFirst {
        cliClasspath.get().asFile.writeText("projects=\nruntime=\n")
        cliParameterNames.get().asFile.writeText("")
    }
    from(cliClasspath)
    from(cliParameterNames)
}
strictCompile {
    ignoreRawTypes() // Raw type used in 'org.gradle.wrapper.Install', fix this or add an ignore/suppress annotation there
}
// ===
