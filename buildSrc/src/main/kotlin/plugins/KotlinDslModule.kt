package plugins

import accessors.java

import build.kotlinDslDebugPropertyName
import build.withTestStrictClassLoading
import build.withTestWorkersMemoryLimits

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType

import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension


/**
 * Configures a Gradle Kotlin DSL module.
 *
 * The assembled jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 */
open class KotlinDslModule : Plugin<Project> {

    override fun apply(project: Project) = project.run {

        apply<KotlinLibrary>()

        // including all sources
        val mainSourceSet = java.sourceSets["main"]
        afterEvaluate {
            tasks.getByName("jar") {
                this as Jar
                from(mainSourceSet.allSource)
                manifest.attributes.apply {
                    put("Implementation-Title", "Gradle Kotlin DSL (${project.name})")
                    put("Implementation-Version", version)
                }
            }
        }

        // sets the Gradle Test Kit user home into custom installation build dir
        if (hasProperty(kotlinDslDebugPropertyName) && findProperty(kotlinDslDebugPropertyName) != "false") {
            tasks.withType<Test> {
                systemProperty(
                    "org.gradle.testkit.dir",
                    "${rootProject.buildDir}/custom/test-kit-user-home")
            }
        }

        withTestStrictClassLoading()
        withTestWorkersMemoryLimits()
    }
}


internal
fun Project.kotlin(action: KotlinProjectExtension.() -> Unit) =
    configure(action)
