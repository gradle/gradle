/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.gradlebuild.unittestandcompile.ModuleType
import build.futureKotlin
import build.kotlin
import build.kotlinVersion
import build.withCompileOnlyGradleApiModulesWithParameterNames
import build.withParallelTests
import codegen.GenerateKotlinDependencyExtensions

plugins {
    `kotlin-dsl-module`
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

withCompileOnlyGradleApiModulesWithParameterNames(":toolingApi")

dependencies {

    compile(project(":kotlinDslToolingModels"))

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

    testImplementation(project(":kotlinDslTestFixtures"))
    testImplementation("com.tngtech.archunit:archunit:0.8.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")
}

// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = file("src/generated/kotlin")

sourceSets.main {
    kotlin {
        srcDir(apiExtensionsOutputDir)
    }
}

val publishedPluginsVersion: String by rootProject.extra("1.1.0") // TODO:kotlin-dsl

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
