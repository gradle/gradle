import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

// tag::avoid-this[]
class MyPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Assumes 'java' plugin is present
        // WARNING: This will fail if the 'java' plugin hasn't been applied yet.
        project.extensions.getByType(JavaPluginExtension).toolchain.languageVersion.set(
            JavaLanguageVersion.of(21)
        )
    }
}
// end::avoid-this[]
