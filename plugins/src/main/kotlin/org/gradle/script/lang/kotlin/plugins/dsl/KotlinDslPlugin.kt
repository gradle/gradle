package org.gradle.script.lang.kotlin.plugins.dsl

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.script.lang.kotlin.gradleScriptKotlinApi
import org.gradle.script.lang.kotlin.plugins.embedded.EmbeddedKotlinPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject

/**
 * The `kotlin-dsl` plugin.
 */
open class KotlinDslPlugin @Inject constructor(val moduleRegistry: ModuleRegistry) : Plugin<Project> {

    override fun apply(project: Project) {
        project.run {

            applyEmbeddedKotlinPlugin()
            addGradleKotlinDslDependency()
            configureCompilerPlugins()
        }
    }


    private
    fun Project.applyEmbeddedKotlinPlugin() {
        plugins.apply(EmbeddedKotlinPlugin::class.java)
    }


    private
    fun Project.addGradleKotlinDslDependency() {
        dependencies.add("compileOnly", gradleScriptKotlinApi())
    }


    private
    fun Project.configureCompilerPlugins() {
        val compilerPluginModule = moduleRegistry.getExternalModule("gradle-script-kotlin-compiler-plugin")
        val compilerPlugin = compilerPluginModule.classpath.asFiles.first()
        require(compilerPlugin.exists()) { "Gradle Kotlin DSL Compiler plugin could not be found! " + compilerPlugin }
        tasks.withType(KotlinCompile::class.java) {
            it.kotlinOptions.freeCompilerArgs += listOf("-Xplugin", compilerPlugin.path)
        }
    }
}
