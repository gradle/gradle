package plugins

import accessors.base
import accessors.java
import accessors.publishing

import codegen.GenerateClasspathManifest

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.api.publish.maven.MavenPublication

import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get


/**
 * Configures a Gradle Kotlin DSL module for publication to artifactory.
 *
 * The published jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 *  - contain a classpath manifest
 */
open class PublicKotlinDslModule : Plugin<Project> {

    override fun apply(project: Project) = project.run {

        apply<KotlinDslModule>()
        apply(plugin = "maven-publish")
        apply(plugin = "com.jfrog.artifactory")

        // with a jar named after `base.archivesBaseName`
        publishing {
            publications.create<MavenPublication>("mavenJava") {
                artifactId = base.archivesBaseName
                from(components["java"])
            }
        }

        tasks.getByName("artifactoryPublish") {
            dependsOn("jar")
        }

        // classpath manifest
        val generatedResourcesDir = file("$buildDir/generate-resources/main")
        val generateClasspathManifest = tasks.create<GenerateClasspathManifest>("generateClasspathManifest") {
            outputDirectory = generatedResourcesDir
        }
        val mainSourceSet = java.sourceSets["main"]
        mainSourceSet.output.dir(
            mapOf("builtBy" to generateClasspathManifest),
            generatedResourcesDir)
    }
}
