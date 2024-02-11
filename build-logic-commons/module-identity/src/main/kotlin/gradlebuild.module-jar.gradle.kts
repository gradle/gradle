import gradlebuild.basics.tasks.ClasspathManifest
import gradlebuild.identity.extension.ModuleIdentityExtension
import java.util.jar.Attributes

plugins {
    id("gradlebuild.module-identity")
}

val moduleIdentity = the<ModuleIdentityExtension>()

configureJarTasks()

pluginManager.withPlugin("java-base") {
    configureClasspathManifestGeneration()
}

fun configureJarTasks() {
    tasks.withType<Jar>().configureEach {
        archiveBaseName = moduleIdentity.baseName
        archiveVersion = moduleIdentity.version.map { it.baseVersion.version }
        manifest.attributes(
            mapOf(
                Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
                Attributes.Name.IMPLEMENTATION_VERSION.toString() to moduleIdentity.version.map { it.baseVersion.version }
            )
        )
    }
}

fun configureClasspathManifestGeneration() {
    val runtimeClasspath by configurations
    val classpathManifest = tasks.register("classpathManifest", ClasspathManifest::class) {
        this.runtimeClasspath.from(runtimeClasspath)
        this.externalDependencies.from(runtimeClasspath.fileCollection { it is ExternalDependency })
        this.manifestFile = moduleIdentity.baseName.map { layout.buildDirectory.file("generated-resources/$it-classpath/$it-classpath.properties").get() }
    }
    sourceSets["main"].output.dir(classpathManifest.map { it.manifestFile.get().asFile.parentFile })
}
