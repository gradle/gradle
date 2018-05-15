import build.*

import codegen.GenerateKotlinDependencyExtensions

plugins {
    id("public-kotlin-dsl-module")
}

base {
    archivesBaseName = "gradle-kotlin-dsl"
}

dependencies {
    compileOnly(gradleApi())

    compile(project(":tooling-models"))
    compile(futureKotlin("stdlib-jdk8"))
    compile(futureKotlin("reflect"))
    compile(futureKotlin("compiler-embeddable"))
    compile(futureKotlin("sam-with-receiver-compiler-plugin")) {
        isTransitive = false
    }

    testCompile(project(":test-fixtures"))
    testCompile("com.squareup.okhttp3:mockwebserver:3.9.1")
}


// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = file("src/generated/kotlin")

java.sourceSets["main"].kotlin {
    srcDir(apiExtensionsOutputDir)
}

val publishedPluginsVersion: String by rootProject.extra

val generateKotlinDependencyExtensions by task<GenerateKotlinDependencyExtensions> {
    outputFile = File(apiExtensionsOutputDir, "org/gradle/kotlin/dsl/KotlinDependencyExtensions.kt")
    embeddedKotlinVersion = kotlinVersion
    kotlinDslPluginsVersion = publishedPluginsVersion
}

val generateExtensions by tasks.creating {
    dependsOn(generateKotlinDependencyExtensions)
}

val compileKotlin by tasks
compileKotlin.dependsOn(generateExtensions)

val clean: Delete by tasks
clean.delete(apiExtensionsOutputDir)


// -- Version manifest properties --------------------------------------
val versionsManifestOutputDir = file("$buildDir/versionsManifest")
val writeVersionsManifest by tasks.creating(WriteProperties::class) {
    outputFile = versionsManifestOutputDir.resolve("gradle-kotlin-dsl-versions.properties")
    property("provider", version)
    property("kotlin", kotlinVersion)
}
java.sourceSets["main"].output.dir(mapOf("builtBy" to writeVersionsManifest), versionsManifestOutputDir)


// -- Testing ----------------------------------------------------------
val prepareIntegrationTestFixtures by rootProject.tasks
val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(prepareIntegrationTestFixtures)
        dependsOn(customInstallation)
    }
}

withParallelTests()

// --- Utility functions -----------------------------------------------
inline fun <reified T : Task> task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)

