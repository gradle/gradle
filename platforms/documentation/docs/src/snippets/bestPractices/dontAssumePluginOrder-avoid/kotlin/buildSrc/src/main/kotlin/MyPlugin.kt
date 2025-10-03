import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

// tag::avoid-this[]
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Assumes 'java' plugin is present
        project.extensions.getByType(JavaPluginExtension::class.java)
            .toolchain.languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
    }
}
// end::avoid-this[]
