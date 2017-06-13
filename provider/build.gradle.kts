import build.*

import codegen.GenerateConfigurationExtensions
import codegen.GenerateKotlinDependencyExtensions

plugins {
    java // so we can benefit from the `java` accessor below
}

base {
    archivesBaseName = "gradle-script-kotlin"
}

dependencies {
    compileOnly(gradleApi())

    compile(project(":compiler-plugin"))
    compile(project(":tooling-models"))
    compile(kotlin("stdlib"))
    compile(kotlin("reflect"))
    compile(kotlin("compiler-embeddable"))

    testCompile(project(":test-fixtures"))
}


// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = file("src/generated/kotlin")

java.sourceSets["main"].kotlin {
    srcDir(apiExtensionsOutputDir)
}

val generateConfigurationExtensions by task<GenerateConfigurationExtensions> {
    outputFile = File(apiExtensionsOutputDir, "org/gradle/script/lang/kotlin/ConfigurationsExtensions.kt")
}

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
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"

inline
fun <reified T : Task> task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)

