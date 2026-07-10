import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

// tag::do-this[]
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // If your plugin requires 'java', apply it so order doesn’t matter
        project.pluginManager.apply("java")
        // Now it's safe to configure Java things immediately
        project.extensions.configure(JavaPluginExtension::class.java) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}
// end::do-this[]
