import org.gradle.api.Project

import org.gradle.script.lang.kotlin.*
import org.gradle.kotlin.dsl.*

/**
 * Configures the current project as a Kotlin project by adding the Kotlin `stdlib` as a dependency.
 */
fun Project.kotlinProject() {
    dependencies {
        compile(kotlin("stdlib"))
    }
}
