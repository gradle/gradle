import gradlebuild.basics.BuildEnvironment
import gradlebuild.kotlindsl.tasks.CheckKotlinCompilerEmbeddableDependencies
import gradlebuild.kotlindsl.tasks.PatchKotlinCompilerEmbeddable

plugins {
    gradlebuild.distribution.`implementation-kotlin`
}

description = "Kotlin Compiler Embeddable - patched for Gradle"

moduleIdentity.baseName.set("kotlin-compiler-embeddable-${libs.kotlinVersion}-patched-for-gradle")

dependencies {
    api(libs.futureKotlin("stdlib"))
    api(libs.futureKotlin("reflect"))
    api(libs.futureKotlin("script-runtime"))
    api(libs.futureKotlin("daemon-embeddable"))

    runtimeOnly(library("trove4j"))
}

val kotlinCompilerEmbeddable by configurations.creating

dependencies {
    kotlinCompilerEmbeddable(project(":distributionsDependencies"))
    kotlinCompilerEmbeddable(libs.futureKotlin("compiler-embeddable"))
}

tasks {

    val checkKotlinCompilerEmbeddableDependencies by registering(CheckKotlinCompilerEmbeddableDependencies::class) {
        current.from(configurations.runtimeClasspath)
        expected.from(kotlinCompilerEmbeddable)
    }

    val patchKotlinCompilerEmbeddable by registering(PatchKotlinCompilerEmbeddable::class) {
        dependsOn(checkKotlinCompilerEmbeddableDependencies)
        excludes.set(listOf(
            "META-INF/services/javax.annotation.processing.Processor",
            "META-INF/native/**/*jansi.*"
        ))
        originalFiles.from(kotlinCompilerEmbeddable)
        dependencies.from(configurations.detachedConfiguration(
            project.dependencies.project(":distributionsDependencies"),
            project.dependencies.create(library("jansi"))
        ))
        dependenciesIncludes.set(mapOf(
            "jansi-" to listOf("META-INF/native/**", "org/fusesource/jansi/internal/CLibrary*.class")
        ))
        additionalRootFiles.from(classpathManifest)

        outputFile.set(jar.get().archiveFile)

        outputs.doNotCacheIfSlowInternetConnection()
    }

    jar {
        dependsOn(patchKotlinCompilerEmbeddable)
        actions.clear()
    }
}

fun TaskOutputs.doNotCacheIfSlowInternetConnection() {
    doNotCacheIf("Slow internet connection") {
        BuildEnvironment.isSlowInternetConnection
    }
}
