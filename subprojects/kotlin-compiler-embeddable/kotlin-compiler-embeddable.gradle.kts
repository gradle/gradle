import build.CheckKotlinCompilerEmbeddableDependencies
import build.PatchKotlinCompilerEmbeddable
import build.futureKotlin
import build.kotlinVersion
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `kotlin-library`
}

description = "Kotlin Compiler Embeddable - patched for Gradle"

base.archivesBaseName = "kotlin-compiler-embeddable-$kotlinVersion-patched-for-gradle"

gradlebuildJava {
    moduleType = ModuleType.CORE
}

dependencies {
    api(futureKotlin("stdlib"))
    api(futureKotlin("reflect"))
    api(futureKotlin("script-runtime"))

    runtimeOnly("org.jetbrains.intellij.deps:trove4j:1.0.20181211")
}

val kotlinCompilerEmbeddable by configurations.creating

dependencies {
    kotlinCompilerEmbeddable(project(":distributionsDependencies"))
    kotlinCompilerEmbeddable(futureKotlin("compiler-embeddable"))
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
    }

    jar {
        dependsOn(patchKotlinCompilerEmbeddable)
        actions.clear()
    }

    val classesDir = layout.buildDirectory.dir("classes/patched")

    val unpackPatchedKotlinCompilerEmbeddable by registering(Sync::class) {
        dependsOn(patchKotlinCompilerEmbeddable)
        from(zipTree(patchKotlinCompilerEmbeddable.get().outputFile))
        into(classesDir)
    }

    sourceSets.main {
        output.dir(files(classesDir).builtBy(unpackPatchedKotlinCompilerEmbeddable))
    }
}
