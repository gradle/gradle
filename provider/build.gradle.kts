import codegen.GenerateClasspathManifest
import codegen.GenerateConfigurationExtensions
import codegen.GenerateKotlinDependencyExtensions

import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    `maven-publish`
    java // so we can benefit from the `java` accessor below
}

apply {
    plugin("kotlin")
}

dependencies {
    compileOnly(gradleApi())

    compile(project(":tooling-models"))
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

base {
    archivesBaseName = "gradle-script-kotlin"
}

publishing {
    publications.create<MavenPublication>("mavenJava") {
        artifactId = base.archivesBaseName
        from(components["java"])
    }
}

// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = file("src/generated/kotlin")

val generateConfigurationExtensions by task<GenerateConfigurationExtensions> {
    outputFile = File(apiExtensionsOutputDir, "org/gradle/script/lang/kotlin/ConfigurationsExtensions.kt")
}

val kotlinVersion: String by rootProject.extra
val kotlinRepo: String by rootProject.extra

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

val prepareIntegrationTestFixtures by rootProject.tasks
val customInstallation by rootProject.tasks

test.dependsOn(prepareIntegrationTestFixtures)
test.dependsOn(customInstallation)


// -- Samples testing --------------------------------------------------
val samplesTestSourceSet = sourceSets.create("samplesTest").apply { configureForTesting() }
val samplesTest = createTestTaskFor(samplesTestSourceSet)
samplesTest.dependsOn(customInstallation)
samplesTest.mustRunAfter(test)
check.dependsOn(samplesTest)


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


// --- Utility functions -----------------------------------------------
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:${rootProject.extra["kotlinVersion"]}"

inline fun <reified T : Task> task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)

