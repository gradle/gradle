import build.*

import codegen.GenerateKotlinDependencyExtensions

plugins {
    java // so we can benefit from the `java` accessor below
}

base {
    archivesBaseName = "gradle-kotlin-dsl"
}

dependencies {
    compileOnly(gradleApi())

    compile(project(":compiler-plugin"))
    compile(project(":tooling-models"))
    compile(futureKotlin("stdlib"))
    compile(futureKotlin("reflect"))
    compile(futureKotlin("compiler-embeddable"))

    testCompile(project(":test-fixtures"))
}


// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = file("src/generated/kotlin")

java.sourceSets["main"].kotlin {
    srcDir(apiExtensionsOutputDir)
}

val generateKotlinDependencyExtensions by task<GenerateKotlinDependencyExtensions> {
    val publishedPluginsVersion: String by rootProject.extra
    outputFile = File(apiExtensionsOutputDir, "org/gradle/kotlin/dsl/KotlinDependencyExtensions.kt")
    embeddedKotlinVersion = kotlinVersion
    kotlinDslPluginsVersion = publishedPluginsVersion
    kotlinDslRepository = kotlinRepo
}

val generateExtensions by tasks.creating {
    dependsOn(generateKotlinDependencyExtensions)
}

val compileKotlin by tasks
compileKotlin.dependsOn(generateExtensions)

val clean: Delete by tasks
clean.delete(apiExtensionsOutputDir)


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
inline
fun <reified T : Task> task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)

