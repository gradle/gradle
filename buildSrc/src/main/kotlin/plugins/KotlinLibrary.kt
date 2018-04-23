package plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


open class KotlinLibrary : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        plugins.apply("kotlin")
        plugins.apply("org.gradle.kotlin.ktlint-convention")

        // TODO: remove once the ktlint-convention plugin is upgraded
        dependencies.add(
            "ktlint",
            "org.jetbrains.kotlin:kotlin-reflect")

        kotlin {
            experimental.coroutines = Coroutines.ENABLE
        }

        tasks.withType(KotlinCompile::class.java) {
            it.kotlinOptions.apply {
                freeCompilerArgs = listOf(
                    "-Xjsr305=strict",
                    "-Xskip-runtime-version-check")
            }
        }
    }
}
