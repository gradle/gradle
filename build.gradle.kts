import codegen.GenerateClasspathManifest
import codegen.GenerateConfigurationExtensions
import codegen.GenerateKotlinDependencyExtensions

import groovy.lang.GroovyObject

import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import org.jfrog.gradle.plugin.artifactory.dsl.ResolverConfig

buildscript {

    var kotlinVersion: String by extra
    kotlinVersion = file("kotlin-version.txt").readText().trim()

    var kotlinRepo: String by extra
    kotlinRepo = "https://repo.gradle.org/gradle/repo"

    repositories {
        maven { url = uri(kotlinRepo) }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    `maven-publish`
    id("com.jfrog.artifactory") version "4.1.1"
    java // so we can benefit from the `java` accessor below
}

apply {
    plugin("kotlin")
}

group = "org.gradle"

version = "0.9.0-SNAPSHOT"

dependencies {
    compileOnly(gradleApi())

    compile(kotlin("stdlib"))
    compile(kotlin("reflect"))
    compile(kotlin("compiler-embeddable"))
}

configure<KotlinProjectExtension> {
    experimental.coroutines = Coroutines.ENABLE
}

val sourceSets = java.sourceSets

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

// -- Testing ----------------------------------------------------------
val check by tasks


// -- Test fixtures ----------------------------------------------------
val fixturesCompile by configurations.creating {
    extendsFrom(configurations.compile)
}
val fixturesRuntime by configurations.creating {
    extendsFrom(configurations.runtime)
}

val fixturesSourceSet = sourceSets.create("fixtures") {
    compileClasspath += mainSourceSet.output
    runtimeClasspath += mainSourceSet.output
    java.srcDirs()
    (this as HasConvention).convention.getPlugin<KotlinSourceSet>().apply {
        kotlin.srcDirs("src/fixtures/kotlin")
    }
}

dependencies {
    fixturesCompile(gradleTestKit())
    fixturesCompile("junit:junit:4.12")
    fixturesCompile("com.nhaarman:mockito-kotlin:1.2.0")
    fixturesCompile("com.fasterxml.jackson.module:jackson-module-kotlin:2.7.5")
    fixturesCompile("org.ow2.asm:asm-all:5.1")
}

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
    description = "Copies latest gradle-script-kotlin snapshot over the custom installation."
    dependsOn(copyCurrentDistro)
    from(configurations.compile)
    from(jar)
    into("$customInstallationDir/lib")
}

fun SourceSet.configureForTesting() {
    configurations["${name}Compile"].extendsFrom(fixturesCompile)
    configurations["${name}Runtime"].extendsFrom(configurations.runtime)
    compileClasspath += mainSourceSet.output + fixturesSourceSet.output
    runtimeClasspath += mainSourceSet.output + fixturesSourceSet.output
    (this as HasConvention).convention.getPlugin<KotlinSourceSet>().apply {
        kotlin.srcDirs("src/$name/kotlin")
    }
}

fun createTestTaskFor(sourceSet: SourceSet) =
    task<Test>(sourceSet.name) {
        group = "verification"
        description = "Runs tests on ${sourceSet.name}."
        classpath = sourceSet.runtimeClasspath
        testClassesDir = sourceSet.output.classesDir
    }


// -- Unit testing ----------------------------------------------------
sourceSets["test"].configureForTesting()
val test by tasks
test.dependsOn(prepareIntegrationTestFixtures)
test.dependsOn(customInstallation)


// -- Samples testing --------------------------------------------------
val samplesTestSourceSet = sourceSets.create("samplesTest").apply { configureForTesting() }
val samplesTest = createTestTaskFor(samplesTestSourceSet)
samplesTest.dependsOn(customInstallation)
check.dependsOn(samplesTest)


// -- Performance testing ----------------------------------------------
val benchmark by tasks.creating(integration.Benchmark::class) {
    dependsOn(customInstallation)
    latestInstallation = customInstallationDir
}


tasks.withType<Test> {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        maxParallelForks = gradle.startParameter.maxWorkerCount / 2 + 1
    }
}

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


// --- Utility functions -----------------------------------------------
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:${extra["kotlinVersion"]}"

operator fun Regex.contains(s: String) = matches(s)

inline fun <reified T : Task> task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)
