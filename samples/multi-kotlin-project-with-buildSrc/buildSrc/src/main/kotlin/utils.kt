import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPluginConvention

import org.gradle.script.lang.kotlin.*

/**
 * Configures the current project as a Kotlin project by applying the `kotlin`
 * plugin and adding the Kotlin `stdlib` as a dependency.
 */
fun Project.kotlinProject() {
    apply { it.plugin("kotlin") }
    dependencies {
        compile(kotlinModule("stdlib"))
    }
}

fun Project.application(configuration: ApplicationPluginConvention.() -> Unit) {
    configure(configuration)
}
