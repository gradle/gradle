import codegen.GenerateClasspathManifest

import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

plugins {
    `maven-publish`
    java // so we can benefit from the `java` accessor below
}

apply {
    plugin("kotlin")
}

configure<KotlinProjectExtension> {
    experimental.coroutines = Coroutines.ENABLE
}

base {
    archivesBaseName = "gradle-script-kotlin-tooling-builders"
}

publishing {
    publications.create<MavenPublication>("mavenJava") {
        artifactId = base.archivesBaseName
        from(components["java"])
    }
}

// --- classpath.properties --------------------------------------------
val generatedResourcesDir = file("$buildDir/generate-resources/main")
val generateClasspathManifest by tasks.creating(GenerateClasspathManifest::class) {
    outputDirectory = generatedResourcesDir
}

java {
    sourceSets {
        "main" {
            output.dir(
                mapOf("builtBy" to generateClasspathManifest),
                generatedResourcesDir)
        }
    }
}

dependencies {
    compile(project(":provider"))
}
