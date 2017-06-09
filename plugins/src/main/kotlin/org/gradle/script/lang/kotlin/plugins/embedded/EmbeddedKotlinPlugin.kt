package org.gradle.script.lang.kotlin.plugins.embedded

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.cache.CacheRepository
import java.io.File
import java.net.URI
import java.util.Properties
import javax.inject.Inject


private
val pluginsProperties: Properties by lazy {
    val properties = Properties()
    val loader = EmbeddedKotlinPlugin::class.java.classLoader
    loader.getResourceAsStream("embedded-kotlin-metadata.properties").use { input ->
        properties.load(input)
    }
    properties
}


val embeddedKotlinVersion =
    pluginsProperties.getProperty("embeddedKotlinVersion")!!


internal
val jetbrainsAnnotationsVersion = "13.0"


internal
data class EmbeddedModule(val group: String,
                          val name: String,
                          val version: String,
                          val dependencies: List<EmbeddedModule>,
                          val autoDependency: Boolean)
{
    val notation: String = "$group:$name:$version"
    val jarRepoPath: String = "${group.replace(".", "/")}/$name/$version/$name-$version.jar"
}


internal
val embeddedModules: List<EmbeddedModule> by lazy {
    val annotations = EmbeddedModule("org.jetbrains", "annotations", jetbrainsAnnotationsVersion, emptyList(), false)
    listOf(
        annotations,
        EmbeddedModule("org.jetbrains.kotlin", "kotlin-stdlib", embeddedKotlinVersion, listOf(annotations), true),
        EmbeddedModule("org.jetbrains.kotlin", "kotlin-reflect", embeddedKotlinVersion, emptyList(), true),
        EmbeddedModule("org.jetbrains.kotlin", "kotlin-compiler-embeddable", embeddedKotlinVersion, emptyList(), false))
}


private
val embeddedRepositoryCacheKeyVersion = 1


open class EmbeddedKotlinPlugin @Inject constructor(val cacheRepository: CacheRepository,
                                                    val moduleRegistry: ModuleRegistry) : Plugin<Project>
{

    override fun apply(project: Project)
    {

        project.run {

            applyKotlinPlugin()

            addRepository()
            addDependencies()
            pinDependencies()
        }
    }

    private
    fun Project.applyKotlinPlugin()
    {
        // `kotlin` and `org.jetbrains.kotlin.jvm` are equivalent
        // the latter id is only available starting with 1.1.1 though
        plugins.apply("kotlin")
    }

    private
    fun Project.addRepository()
    {
        repositories.maven { repo ->
            repo.name = "Embedded Kotlin Repository"
            repo.url = initializeRepository()
        }
    }

    private
    fun Project.initializeRepository(): URI
    {
        val cacheKey = "embedded-kotlin-rep-$embeddedKotlinVersion-$embeddedRepositoryCacheKeyVersion"
        cacheRepository.cache(cacheKey).withInitializer { cache ->
            embeddedModules.forEach { module ->
                val fromDistro = moduleRegistry.getExternalModule(module.name).classpath.asFiles.first()
                fromDistro.copyTo(File(File(cache.baseDir, "repo"), module.jarRepoPath))
            }
        }.open().use { cache ->
            return uri(File(cache.baseDir, "repo"))
        }
    }

    private
    fun Project.addDependencies()
    {
        embeddedModules.filter { it.autoDependency }.forEach { embeddedModule ->
            dependencies.add("implementation", clientModuleFor(embeddedModule))
        }
    }

    private
    fun Project.clientModuleFor(embeddedModule: EmbeddedModule): ClientModule
    {
        val clientModule = dependencies.module(embeddedModule.notation) as ClientModule
        embeddedModule.dependencies.forEach { dependency ->
            clientModule.addDependency(clientModuleFor(dependency))
        }
        return clientModule
    }

    private
    fun Project.pinDependencies()
    {
        configurations.all { configuration ->
            configuration.resolutionStrategy.eachDependency { details ->
                findEmbeddedModule(details.requested)?.let { module ->
                    details.useTarget(module.notation)
                }
            }
        }
    }

    private
    fun findEmbeddedModule(requested: ModuleVersionSelector)
        = embeddedModules.find { it.group == requested.group && it.name == requested.name }
}
