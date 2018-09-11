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

tasks {

    val generateKotlinDependencyExtensions by registering(GenerateKotlinDependencyExtensions::class) {
        outputFile.set(apiExtensionsOutputDir.resolve("org/gradle/kotlin/dsl/KotlinDependencyExtensions.kt"))
        embeddedKotlinVersion.set(kotlinVersion)
        kotlinDslPluginsVersion.set(publishedPluginsVersion)
    }

    val generateExtensions by registering {
        dependsOn(generateKotlinDependencyExtensions)
    }

    "compileKotlin" {
        dependsOn(generateExtensions)
    }

    "clean"(Delete::class) {
        delete(apiExtensionsOutputDir)
    }

    "compileTestJava"(JavaCompile::class) {
        // `kotlin-compiler-embeddable` brings the `javaslang.match.PatternsProcessor`
        // annotation processor to the classpath which causes Gradle to emit a deprecation warning.
        // `-proc:none` disables annotation processing and gets rid of the warning.
        options.compilerArgs.add("-proc:none")
    }

// -- Version manifest properties --------------------------------------
    val versionsManifestOutputDir = file("$buildDir/versionsManifest")
    val writeVersionsManifest by registering(WriteProperties::class) {
        outputFile = versionsManifestOutputDir.resolve("gradle-kotlin-dsl-versions.properties")
        property("provider", version)
        property("kotlin", kotlinVersion)
    }

    "processResources"(ProcessResources::class) {
        from(writeVersionsManifest)
    }

// -- Testing ----------------------------------------------------------
// Disable incremental compilation for Java fixture sources
// Incremental compilation is causing OOMEs with our low build daemon heap settings
    withType(JavaCompile::class).named("compileTestJava").configure {
        options.isIncremental = false
    }

    "test" {
        dependsOn(":customInstallation")
    }
}

withParallelTests()
