import codegen.GenerateClasspathManifest
import org.gradle.api.internal.HasConvention
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    `maven-publish`
    java
}

apply {
    plugin("kotlin")
}

base {
    archivesBaseName = "gradle-script-kotlin-tooling-models"
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
            /*
            java.setSrcDirs(emptyList<String>())
            (this as HasConvention).run {
                convention.getPlugin<KotlinSourceSet>().apply {
                    kotlin.setSrcDirs(listOf("src/main/java", "src/main/kotlin"))
                }
            }
            */
        }
    }
}
