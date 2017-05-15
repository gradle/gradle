package plugins

import codegen.GenerateClasspathManifest

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension


/**
 * Configures a Gradle Script Kotlin module for publication.
 *
 * The published jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 *  - contain a classpath manifest
 */
open class GskModule : Plugin<Project> {

    override fun apply(project: Project) {

        project.run {

            // A module is a Kotlin project published to artifactory
            applyPlugins(
                "kotlin",
                "maven-publish",
                "com.jfrog.artifactory")

            kotlin {
                experimental.coroutines = Coroutines.ENABLE
            }


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


            // including all sources
            val mainSourceSet = java.sourceSets.getByName("main")
            afterEvaluate {
                tasks.getByName("jar") {
                    (it as Jar).run {
                        from(mainSourceSet.allSource)
                        manifest.attributes.apply {
                            put("Implementation-Title", "Gradle Script Kotlin (${project.name})")
                            put("Implementation-Version", version)
                        }
                    }
                }
            }


            // and a classpath manifest
            val generatedResourcesDir = file("$buildDir/generate-resources/main")
            val generateClasspathManifest = tasks.create("generateClasspathManifest", GenerateClasspathManifest::class.java) {
                it.outputDirectory = generatedResourcesDir
            }
            mainSourceSet.output.dir(
                mapOf("builtBy" to generateClasspathManifest),
                generatedResourcesDir)
        }
    }

    private
    fun Project.applyPlugins(vararg plugins: String) =
        apply { action -> plugins.forEach { action.plugin(it) } }

    private
    val Project.base get() = convention.getPlugin(BasePluginConvention::class.java)

    private
    val Project.java get() = convention.getPlugin(JavaPluginConvention::class.java)

    private
    fun Project.publishing(action: PublishingExtension.() -> Unit) =
        extensions.configure(PublishingExtension::class.java, action)

    private
    fun Project.kotlin(action: KotlinProjectExtension.() -> Unit) =
        extensions.configure(KotlinProjectExtension::class.java, action)
}
