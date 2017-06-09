package plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension


/**
 * Configures a Gradle Script Kotlin module.
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
        }
    }


    private
    val Project.java get() = convention.getPlugin(JavaPluginConvention::class.java)


    private
    fun Project.kotlin(action: KotlinProjectExtension.() -> Unit) =
        extensions.configure(KotlinProjectExtension::class.java, action)
}
