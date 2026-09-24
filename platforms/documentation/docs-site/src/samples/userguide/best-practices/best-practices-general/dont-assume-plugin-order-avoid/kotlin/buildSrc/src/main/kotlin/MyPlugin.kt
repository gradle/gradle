import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

// tag::avoid-this[]
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Assumes 'java' plugin is present
        // WARNING: This will fail if the 'java' plugin hasn't been applied yet.
        project.extensions.getByType(JavaPluginExtension::class.java).toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}
// end::avoid-this[]
