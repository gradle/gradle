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
    moduleType = ModuleType.INTERNAL
}

dependencies {

    api(project(":distributionsDependencies"))

    compile(futureKotlin("stdlib"))
    compile(futureKotlin("reflect"))
    compile(futureKotlin("script-runtime"))

    runtime("org.jetbrains.intellij.deps:trove4j:1.0.20181211")
}

tasks {

    val patchKotlinCompilerEmbeddable by registering(PatchKotlinCompilerEmbeddable::class) {
        excludes.set(listOf(
            "META-INF/services/javax.annotation.processing.Processor",
            "META-INF/native/**/*jansi.*"
        ))
        dependencies.from(configurations.detachedConfiguration(
            project.dependencies.project(":distributionsDependencies"),
            project.dependencies.create(futureKotlin("compiler-embeddable")),
            project.dependencies.create(library("jansi"))
        ))
        dependenciesIncludes.set(mapOf(
            "jansi-" to listOf("META-INF/native/**", "org/fusesource/jansi/internal/CLibrary*.class")
        ))
        additionalFiles = fileTree(classpathManifest.get().manifestFile.parentFile) {
            include(classpathManifest.get().manifestFile.name)
        }
        outputFile.set(jar.get().archiveFile)
    }

    // Replace this project JAR with the patched kotlin-compiler-embeddable JAR
    jar {
        dependsOn(patchKotlinCompilerEmbeddable)
        actions.clear()
    }
}
