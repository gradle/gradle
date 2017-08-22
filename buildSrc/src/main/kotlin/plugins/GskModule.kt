package plugins

import build.kotlinDslDebugPropertyName
import build.withTestWorkersMemoryLimits

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.api.plugins.JavaPluginConvention

import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


/**
 * Configures a Gradle Kotlin DSL module.
 *
 * The assembled jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 */
open class GskModule : Plugin<Project> {

    override fun apply(project: Project) {

        project.run {

            plugins.apply("kotlin")

            kotlin {
                experimental.coroutines = Coroutines.ENABLE
            }

            tasks.withType(KotlinCompile::class.java) {
                it.kotlinOptions.apply {
                    freeCompilerArgs = listOf("-Xjsr305-annotations=enable")
                }
            }

            // including all sources
            val mainSourceSet = java.sourceSets.getByName("main")
            afterEvaluate {
                tasks.getByName("jar") {
                    (it as Jar).run {
                        from(mainSourceSet.allSource)
                        manifest.attributes.apply {
                            put("Implementation-Title", "Gradle Kotlin DSL (${project.name})")
                            put("Implementation-Version", version)
                        }
                    }
                }
            }

            // sets the Gradle Test Kit user home into custom installation build dir
            if (hasProperty(kotlinDslDebugPropertyName) && findProperty(kotlinDslDebugPropertyName) != "false") {
                tasks.withType(Test::class.java) { testTask ->
                    testTask.systemProperty(
                        "org.gradle.testkit.dir",
                        "${rootProject.buildDir}/custom/test-kit-user-home")
                }
            }

            withTestWorkersMemoryLimits()
        }
    }

    private
    fun Project.kotlin(action: KotlinProjectExtension.() -> Unit) =
        extensions.configure(KotlinProjectExtension::class.java, action)
}


internal
val Project.java
    get() = convention.getPlugin(JavaPluginConvention::class.java)
