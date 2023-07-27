import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.jar.Attributes

plugins {
    id("gradlebuild.distribution.api-java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
//    id("java")
}

description = "Bootstraps a Gradle build initiated by the gradlew script"

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":cli"))
    implementation(project(":wrapper-shared"))

    testImplementation(project(":base-services"))
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

//val executableJar by tasks.registering(Jar::class) {
//    archiveFileName = "gradle-wrapper.jar"
//    manifest {
//        attributes.remove(Attributes.Name.IMPLEMENTATION_VERSION.toString())
//        attributes(Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle Wrapper")
//    }
//    from(sourceSets.main.get().output)
//    from(configurations.runtimeClasspath.get().incoming.artifactView {
//        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
//    }.files)
//}
//
//tasks.jar {
//    from(executableJar)
//}

val executableJar by tasks.registering(ShadowJar::class) {
    archiveFileName = "gradle-wrapper.jar"
    manifest {
        attributes.remove(Attributes.Name.IMPLEMENTATION_VERSION.toString())
        attributes(Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle Wrapper")
    }
    from(sourceSets.main.get().output)
    from(project.configurations.runtimeClasspath.get().incoming.artifactView {
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
    }.files)

    minimize {
        exclude(project(":wrapper-shared"))
    }
}

tasks.jar {
    from(executableJar)
}
