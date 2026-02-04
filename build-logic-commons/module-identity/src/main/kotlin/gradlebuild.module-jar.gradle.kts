import gradlebuild.identity.extension.GradleModuleExtension
import java.util.jar.Attributes

plugins {
    id("gradlebuild.module-identity")
}

val gradleModule = the<GradleModuleExtension>()

configureJarTasks()

fun configureJarTasks() {
    tasks.withType<Jar>().configureEach {
        archiveBaseName = gradleModule.identity.baseName
        archiveVersion = gradleModule.identity.version.map { it.baseVersion.version }
        manifest.attributes(
            mapOf(
                Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
                Attributes.Name.IMPLEMENTATION_VERSION.toString() to gradleModule.identity.version.map { it.baseVersion.version }
            )
        )
    }
}

