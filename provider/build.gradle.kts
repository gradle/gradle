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

    testCompile(project(":test-fixtures"))
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
val test by tasks

val prepareIntegrationTestFixtures by rootProject.tasks
val customInstallation by rootProject.tasks

test.dependsOn(prepareIntegrationTestFixtures)
test.dependsOn(customInstallation)


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

