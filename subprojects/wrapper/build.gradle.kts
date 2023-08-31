import com.gradleup.gr8.EmbeddedJarTask
import com.gradleup.gr8.Gr8Task
import java.util.jar.Attributes

plugins {
    id("gradlebuild.distribution.api-java")
    id("com.gradleup.gr8") version "0.9"
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

val executableJar by tasks.registering(Jar::class) {
    archiveFileName = "gradle-wrapper-executable.jar"
    manifest {
        attributes.remove(Attributes.Name.IMPLEMENTATION_VERSION.toString())
        attributes(Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle Wrapper")
    }
    from(sourceSets.main.get().output)
}

gr8 {
    create("gr8") {
        // TODO This should work by passing `executableJar` directly to th Gr8 plugin
        programJar(executableJar.flatMap { it.archiveFile })
        archiveName("gradle-wrapper.jar")
        configuration("runtimeClasspath")
        proguardFile("src/main/proguard/wrapper.pro")
        // Exclude anything from Guava etc. except the actual manifest
        exclude("META-INF/(?!MANIFEST.MF).*")
    }
}

// TODO This dependency should be configured by the Gr8 plugin
tasks.named<EmbeddedJarTask>("gr8EmbeddedJar") {
    dependsOn(executableJar)
}

tasks.jar {
    from(tasks.named<Gr8Task>("gr8R8Jar").flatMap { it.outputJar() })
}
