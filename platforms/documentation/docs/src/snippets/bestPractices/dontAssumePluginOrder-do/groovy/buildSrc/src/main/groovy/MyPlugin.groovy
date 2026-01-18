import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

// tag::do-this[]
class MyPlugin implements Plugin<Project> {
    void apply(Project project) {
        // If your plugin requires 'java', apply it so order doesn’t matter
        project.pluginManager.apply('java')
        // Now it's safe to configure Java things immediately
        project.extensions.configure(JavaPluginExtension) {
            it.toolchain {
                it.languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }
}
// end::do-this[]
