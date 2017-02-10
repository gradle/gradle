import codegen.GenerateClasspathManifest
import codegen.GenerateConfigurationExtensions
import codegen.GenerateKotlinDependencyExtensions

import groovy.lang.GroovyObject

import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.HasConvention
import org.gradle.api.publish.*
import org.gradle.api.publish.maven.*
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.GradleBuild
import org.gradle.jvm.tasks.Jar

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import org.jfrog.gradle.plugin.artifactory.dsl.ResolverConfig

import java.io.File

buildscript {

    var kotlinVersion: String by extra
    kotlinVersion = file("kotlin-version.txt").readText().trim()

    var kotlinRepo: String by extra
    kotlinRepo = "https://repo.gradle.org/gradle/repo"

    repositories {
        maven { setUrl(kotlinRepo) }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    `maven-publish`
    id("com.jfrog.artifactory") version "4.1.1"
}

apply {
    plugin("kotlin")
}

group = "org.gradle"

version = "0.8.0-SNAPSHOT"

dependencies {
    compileOnly(gradleApi())

    compile("org.codehaus.groovy:groovy-all:2.4.7")
    compile("org.slf4j:slf4j-api:1.7.10")
    compile("javax.inject:javax.inject:1")

    compile(kotlin("stdlib"))
    compile(kotlin("reflect"))
    compile(kotlin("compiler-embeddable"))

    testCompile(gradleTestKit())
    testCompile("junit:junit:4.12")
    testCompile("com.nhaarman:mockito-kotlin:1.2.0")
    testCompile("com.fasterxml.jackson.module:jackson-module-kotlin:2.7.5")
    testCompile("org.ow2.asm:asm-all:5.1")
}


val sourceSets = the<JavaPluginConvention>().sourceSets

val mainSourceSet = sourceSets["main"]

val jar: Jar by tasks
jar.apply {
    from(mainSourceSet.allSource)
    manifest.attributes.apply {
        put("Implementation-Title", "Gradle Script Kotlin")
        put("Implementation-Version", version)
    }
}

publishing {
    publications.create<MavenPublication>("mavenJava") {
        from(components["java"])
    }
}

// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = file("src/generated/kotlin")

val generateConfigurationExtensions by task<GenerateConfigurationExtensions> {
    outputFile = File(apiExtensionsOutputDir, "org/gradle/script/lang/kotlin/ConfigurationsExtensions.kt")
}

val kotlinVersion: String by extra

val kotlinRepo: String by extra

val generateKotlinDependencyExtensions by task<GenerateKotlinDependencyExtensions> {
    outputFile = File(apiExtensionsOutputDir, "org/gradle/script/lang/kotlin/KotlinDependencyExtensions.kt")
    embeddedKotlinVersion = kotlinVersion
    gradleScriptKotlinRepository = kotlinRepo
}

val generateExtensions by tasks.creating {
    dependsOn(generateConfigurationExtensions)
    dependsOn(generateKotlinDependencyExtensions)
}

val compileKotlin by tasks
compileKotlin.dependsOn(generateExtensions)

(mainSourceSet as HasConvention).convention.getPlugin<KotlinSourceSet>().apply {
    kotlin.srcDir(apiExtensionsOutputDir)
}

// -- Performance testing ----------------------------------------------
//
// 1. Creates a custom Gradle installation with latest gradle-script-kotlin jar
//
// 2. Benchmarks latest installation against configured wrapper
//
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
    description = "Copies latest gradle-script-kotlin snapshot over the custom installation."
    dependsOn(copyCurrentDistro)
    from(configurations.compile)
    from(jar)
    into("$customInstallationDir/lib")
}

val test by tasks
test.dependsOn(customInstallation)

val benchmark by task<integration.Benchmark> {
    dependsOn(customInstallation)
    latestInstallation = customInstallationDir
}


// -- Integration testing ---------------------------------------------
//
// Checks a single sample, for instance:
//
//     check-hello-kotlin
//
tasks.addRule("Pattern: check-<SAMPLE>") {
    val taskName = this
    if (taskName.startsWith("check-")) {
        val checkSample = task<integration.CheckSample>("$taskName-task") {
            dependsOn(customInstallation)
            installation = customInstallationDir
            sampleDir = file("samples/${taskName.removePrefix("check-")}")
        }
        task(taskName).dependsOn(checkSample)
    }
}

val checkSamples by tasks.creating {
    description = "Checks all samples"
    file("samples").listFiles().forEach {
        if (it.isDirectory && !it.name.contains("android")) {
            dependsOn("check-${it.name}")
        }
    }
}

val check by tasks
check.dependsOn(checkSamples)

val prepareIntegrationTestFixtures by task<GradleBuild> {
    setDir(file("fixtures"))
}
// See #189
//test.dependsOn(prepareIntegrationTestFixtures)


// --- classpath.properties --------------------------------------------
val generatedResourcesDir = file("$buildDir/generate-resources/main")
val generateClasspathManifest by task<GenerateClasspathManifest> {
    outputDirectory = generatedResourcesDir
}
mainSourceSet.output.dir(
    mapOf("builtBy" to generateClasspathManifest),
    generatedResourcesDir)


// --- Configure publications ------------------------------------------
fun buildTagFor(version: String): String =
    when (version.substringAfterLast('-')) {
        "SNAPSHOT" -> "snapshot"
        in Regex("""M\d+[a-z]*$""") -> "milestone"
        else -> "release"
    }

configure<ArtifactoryPluginConvention> {
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


// --- Utility functions -----------------------------------------------
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:${extra["kotlinVersion"]}"

fun publishing(setup: PublishingExtension.() -> Unit) = configure(setup)

operator fun Regex.contains(s: String) = matches(s)

inline fun <reified T : Task> task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)
