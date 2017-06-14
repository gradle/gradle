import org.gradle.api.Project

import org.gradle.script.lang.kotlin.*

/**
 * Configures the current project as a Kotlin project by applying the `kotlin`
 * plugin and adding the Kotlin `stdlib` as a dependency.
 */
fun Project.kotlinProject() {
    apply { plugin("kotlin") }
    dependencies {
        compile(kotlin("stdlib"))
    }
}

