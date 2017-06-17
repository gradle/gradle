import groovy.lang.GroovyObject

import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import org.jfrog.gradle.plugin.artifactory.dsl.ResolverConfig
import org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask

buildscript {

    build.loadExtraPropertiesOf(project)

    val kotlinRepo: String by extra
    repositories {
        maven { url = uri(kotlinRepo) }
    }

    val kotlinVersion: String by extra
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    base
    id("com.jfrog.artifactory") version "4.1.1"
}

allprojects {
    group = "org.gradle"
    version = "0.10.0-SNAPSHOT"
}

val pluginsCurrentVersion by extra { "0.10.7" }
val pluginsNextVersion = "0.10.8"
project(":plugins") {
    group = "org.gradle.kotlin"
    version = pluginsNextVersion
}

// --- Configure publications ------------------------------------------
val publishedProjects =
    listOf(
        project(":provider"),
        project(":compiler-plugin"),
        project(":tooling-models"),
        project(":tooling-builders"))

// For documentation and meaningful `./gradlew dependencies` output
val distribution by configurations.creating
dependencies {
    publishedProjects.forEach {
        distribution(it)
    }
}

configure(publishedProjects) {
    apply<plugins.GskPublishedModule>()
}

tasks {
    // Disable publication on root project
    "artifactoryPublish"(ArtifactoryTask::class) {
        skip = true
    }
}

artifactory {
    setContextUrl("https://repo.gradle.org/gradle")
    publish(delegateClosureOf<PublisherConfig> {
        repository(delegateClosureOf<GroovyObject> {
            val targetRepoKey = "libs-${buildTagFor(project.version as String)}s-local"
            setProperty("repoKey", targetRepoKey)
            setProperty("username", project.findProperty("artifactory_user") ?: "nouser")
            setProperty("password", project.findProperty("artifactory_password") ?: "nopass")
            setProperty("maven", true)
        })
        defaults(delegateClosureOf<GroovyObject> {
            invokeMethod("publications", "mavenJava")
        })
    })
    resolve(delegateClosureOf<ResolverConfig> {
        setProperty("repoKey", "repo")
    })
}

fun buildTagFor(version: String): String =
    when (version.substringAfterLast('-')) {
        "SNAPSHOT"                  -> "snapshot"
        in Regex("""M\d+[a-z]*$""") -> "milestone"
        else                        -> "release"
    }


// -- Integration testing ----------------------------------------------
val prepareIntegrationTestFixtures by task<GradleBuild> {
    dir = file("fixtures")
}

val customInstallationDir = file("$buildDir/custom/gradle-${gradle.gradleVersion}")

val copyCurrentDistro by task<Copy> {
    description = "Copies the current Gradle distro into '$customInstallationDir'."

    from(gradle.gradleHomeDir)
    into(customInstallationDir)
    exclude("**/*kotlin*")

    // preserve last modified date on each file to make it easier
    // to check which files were patched by next step
    val copyDetails = mutableListOf<FileCopyDetails>()
    eachFile { copyDetails.add(this) }
    doLast {
        copyDetails.forEach { details ->
            File(customInstallationDir, details.path).setLastModified(details.lastModified)
        }
    }

    // don't bother recreating it
    onlyIf { !customInstallationDir.exists() }
}

val customInstallation by task<Copy> {
    description = "Copies latest gradle-kotlin-dsl snapshot over the custom installation."
    dependsOn(copyCurrentDistro)
    from(distribution)
    into("$customInstallationDir/lib")
}


// -- Performance testing ----------------------------------------------
val benchmark by task<integration.Benchmark> {
    dependsOn(customInstallation)
    latestInstallation = customInstallationDir
}


// --- Utility functions -----------------------------------------------
operator fun Regex.contains(s: String) = matches(s)

inline
fun <reified T : Task> task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)
