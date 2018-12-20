import build.*

import codegen.GenerateKotlinDependencyExtensions

plugins {
    `public-kotlin-dsl-module`
}

base {
    archivesBaseName = "gradle-kotlin-dsl"
}

dependencies {
    compileOnly(gradleApiWithParameterNames())

    compile(project(":tooling-models"))
    compile(futureKotlin("stdlib-jdk8"))
    compile(futureKotlin("reflect"))
    compile(futureKotlin("script-runtime"))
    compile(futureKotlin("compiler-embeddable"))
    compile(futureKotlin("sam-with-receiver-compiler-plugin")) {
        isTransitive = false
    }
    compile("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.0.4") {
        isTransitive = false
    }

    testImplementation(project(":test-fixtures"))
    testImplementation("com.tngtech.archunit:archunit:0.8.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.1.0")
}

// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = file("src/generated/kotlin")

sourceSets.main {
    kotlin {
        srcDir(apiExtensionsOutputDir)
    }
}

val publishedPluginsVersion: String by rootProject.extra

tasks {

    val generateKotlinDependencyExtensions by registering(GenerateKotlinDependencyExtensions::class) {
        outputFile = File(apiExtensionsOutputDir, "org/gradle/kotlin/dsl/KotlinDependencyExtensions.kt")
        embeddedKotlinVersion = kotlinVersion
        kotlinDslPluginsVersion = publishedPluginsVersion
    }

    val generateExtensions by registering {
        dependsOn(generateKotlinDependencyExtensions)
    }

    compileKotlin {
        dependsOn(generateExtensions)
    }

    clean {
        delete(apiExtensionsOutputDir)
    }

// -- Version manifest properties --------------------------------------
    val versionsManifestOutputDir = file("$buildDir/versionsManifest")
    val writeVersionsManifest by registering(WriteProperties::class) {
        outputFile = versionsManifestOutputDir.resolve("gradle-kotlin-dsl-versions.properties")
        property("provider", version)
        property("kotlin", kotlinVersion)
    }

    processResources {
        from(writeVersionsManifest)
    }

// -- Testing ----------------------------------------------------------
    compileTestJava {
        // Disable incremental compilation for Java fixture sources
        // Incremental compilation is causing OOMEs with our low build daemon heap settings
        options.isIncremental = false
        // `kotlin-compiler-embeddable` brings the `javaslang.match.PatternsProcessor`
        // annotation processor to the classpath which causes Gradle to emit a deprecation warning.
        // `-proc:none` disables annotation processing and gets rid of the warning.
        options.compilerArgs.add("-proc:none")
    }

    test {
        dependsOn(":customInstallation")
    }
}

withParallelTests()
