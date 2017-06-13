package plugins

import codegen.GenerateClasspathManifest

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.api.plugins.BasePluginConvention

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication


/**
 * Configures a Gradle Script Kotlin module for publication to artifactory.
 *
 * The published jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 *  - contain a classpath manifest
 */
open class GskPublishedModule : Plugin<Project> {

    override fun apply(project: Project) {

        project.run {

            plugins.apply(GskModule::class.java)
            plugins.apply("maven-publish")
            plugins.apply("com.jfrog.artifactory")

            // with a jar named after `base.archivesBaseName`
            publishing {
                publications.create("mavenJava", MavenPublication::class.java) {
                    it.artifactId = base.archivesBaseName
                    it.from(components.getByName("java"))
                }
            }

            tasks.getByName("artifactoryPublish") {
                it.dependsOn("jar")
            }

            // classpath manifest
            val generatedResourcesDir = file("$buildDir/generate-resources/main")
            val generateClasspathManifest = tasks.create("generateClasspathManifest", GenerateClasspathManifest::class.java) {
                it.outputDirectory = generatedResourcesDir
            }
            val mainSourceSet = java.sourceSets.getByName("main")
            mainSourceSet.output.dir(
                mapOf("builtBy" to generateClasspathManifest),
                generatedResourcesDir)
        }
    }

    private
    val Project.base
        get() = convention.getPlugin(BasePluginConvention::class.java)

    private
    fun Project.publishing(action: PublishingExtension.() -> Unit) =
        extensions.configure(PublishingExtension::class.java, action)
}
