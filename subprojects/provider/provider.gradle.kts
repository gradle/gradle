import build.*

import codegen.GenerateKotlinDependencyExtensions

plugins {
    id("public-kotlin-dsl-module")
}

base {
    archivesBaseName = "gradle-kotlin-dsl"
}

dependencies {
    compileOnly(gradleApiWithParameterNames())

    compile(project(":tooling-models"))
    compile(futureKotlin("stdlib-jdk8"))
    compile(futureKotlin("reflect"))
    compile(futureKotlin("compiler-embeddable"))
    compile(futureKotlin("sam-with-receiver-compiler-plugin")) {
        isTransitive = false
    }

    testCompile(project(":test-fixtures"))
    testCompile("com.tngtech.archunit:archunit:0.8.3")
}


// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = file("src/generated/kotlin")

sourceSets["main"].kotlin {
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
val processResources by tasks.getting(ProcessResources::class) {
    from(writeVersionsManifest)
}

// -- Testing ----------------------------------------------------------
// Disable incremental compilation for Java fixture sources
// Incremental compilation is causing OOMEs with our low build daemon heap settings
tasks.withType(JavaCompile::class.java).named("compileTestJava").configure {
    options.isIncremental = false
}

val customInstallation by rootProject.tasks

tasks.named("test").configure {
    dependsOn(customInstallation)
}

withParallelTests()

// --- Utility functions -----------------------------------------------
inline fun <reified T : Task> task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)
