/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.precompile.v1

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.PluginAware
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.DefaultKotlinScript
import org.gradle.kotlin.dsl.support.defaultKotlinScriptHostForGradle
import org.gradle.kotlin.dsl.support.defaultKotlinScriptHostForProject
import org.gradle.kotlin.dsl.support.defaultKotlinScriptHostForSettings
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependency
import org.gradle.plugin.use.PluginDependencySpec
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import kotlin.script.dependencies.Environment
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.isStandalone
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.extensions.SamWithReceiverAnnotations
import kotlin.script.templates.ScriptTemplateDefinition


/**
 * Base script template for compilation of `plugins {}` blocks extracted from precompiled scripts.
 */
@KotlinScript(
    fileExtension = "gradle.kts",
    compilationConfiguration = PrecompiledPluginsBlockCompilationConfiguration::class
)
@ScriptTemplateDefinition
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@GradleDsl
open class PrecompiledPluginsBlock(private val pluginDependencies: PluginDependenciesSpec) {

    fun plugins(configuration: PluginDependenciesSpecScope.() -> Unit) {
        PluginDependenciesSpecScope(pluginDependencies).configuration()
    }
}

internal
object PrecompiledPluginsBlockCompilationConfiguration : ScriptCompilationConfiguration({
    isStandalone(false)
    baseClass(PrecompiledPluginsBlock::class)
    defaultImportsForPrecompiledScript()
})


/**
 * Script template definition for precompiled Kotlin script targeting [Gradle] instances.
 *
 * @see PrecompiledProjectScript
 */
@KotlinScript(
    fileExtension = "init.gradle.kts",
    compilationConfiguration = PrecompiledInitScriptCompilationConfiguration::class
)
@ScriptTemplateDefinition
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@GradleDsl
open class PrecompiledInitScript(
    target: Gradle
) : DefaultKotlinScript(defaultKotlinScriptHostForGradle(target)), PluginAware by target


/**
 * Script template definition for precompiled Kotlin script targeting [Settings] instances.
 *
 * @see PrecompiledProjectScript
 */
@KotlinScript(
    fileExtension = "settings.gradle.kts",
    compilationConfiguration = PrecompiledSettingsScriptCompilationConfiguration::class
)
@ScriptTemplateDefinition
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@GradleDsl
open class PrecompiledSettingsScript(
    target: Settings
) : DefaultKotlinScript(defaultKotlinScriptHostForSettings(target)), PluginAware by target {

    /**
     * Configures the plugin dependencies for this settings script.
     *
     * @see [PluginDependenciesSpec]
     */
    @Suppress("unused")
    fun plugins(block: PluginDependenciesSpec.() -> Unit) {
        block(
            object : PluginDependenciesSpec {
                override fun id(id: String): PluginDependencySpec {
                    pluginManager.apply(id)
                    return NullPluginDependencySpec
                }

                override fun alias(notation: Provider<PluginDependency>): PluginDependencySpec {
                    pluginManager.apply(notation.get().pluginId)
                    return NullPluginDependencySpec
                }

                override fun alias(notation: ProviderConvertible<PluginDependency>): PluginDependencySpec {
                    return alias(notation.asProvider())
                }
            }
        )
    }
}


/**
 * Script template definition for precompiled Kotlin scripts targeting [Project] instances.
 *
 * A precompiled script is a script compiled as part of a regular Kotlin source-set and distributed
 * in the usual way, java class files packaged in some library, meant to be consumed as a binary
 * Gradle plugin.
 *
 * The Gradle plugin id by which the precompiled script can be referenced is derived from its name
 * and package declaration - if any - in the following fashion:
 *
 * ```kotlin
 *     fun pluginIdFor(script: File, packageName: String?) =
 *         (packageName?.let { "$it." } ?: "") + script.nameWithoutExtension
 * ```
 *
 * Thus, the script `src/main/kotlin/code-quality.gradle.kts` would be exposed as the `code-quality`
 * plugin (assuming it has no package declaration) whereas the script
 * `src/main/kotlin/gradlebuild/code-quality.gradle.kts` would be exposed as the `gradlebuild.code-quality`
 * plugin, again assuming it has the matching package declaration.
 */
@KotlinScript(
    fileExtension = "gradle.kts",
    compilationConfiguration = PrecompiledProjectScriptCompilationConfiguration::class
)
@ScriptTemplateDefinition
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@GradleDsl
open class PrecompiledProjectScript(
    target: Project
) : DefaultKotlinScript(defaultKotlinScriptHostForProject(target)), PluginAware by target {

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    @Suppress("unused")
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit) {
        error("The `buildscript` block is not supported on Kotlin script plugins, please use the `plugins` block or project level dependencies.")
    }

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    @Suppress("unused")
    fun plugins(block: PluginDependenciesSpec.() -> Unit) {
        block(
            object : PluginDependenciesSpec {
                override fun id(id: String): PluginDependencySpec {
                    pluginManager.apply(id)
                    return NullPluginDependencySpec
                }

                override fun alias(notation: Provider<PluginDependency>): PluginDependencySpec {
                    pluginManager.apply(notation.get().pluginId)
                    return NullPluginDependencySpec
                }

                override fun alias(notation: ProviderConvertible<PluginDependency>): PluginDependencySpec {
                    return alias(notation.asProvider())
                }
            }
        )
    }
}


object PrecompiledInitScriptCompilationConfiguration : ScriptCompilationConfiguration({
    isStandalone(false)
    baseClass(PrecompiledInitScript::class)
    implicitReceivers(Gradle::class)
    defaultImportsForPrecompiledScript()
})


object PrecompiledSettingsScriptCompilationConfiguration : ScriptCompilationConfiguration({
    isStandalone(false)
    baseClass(PrecompiledSettingsScript::class)
    implicitReceivers(Settings::class)
    defaultImportsForPrecompiledScript()
})


object PrecompiledProjectScriptCompilationConfiguration : ScriptCompilationConfiguration({
    isStandalone(false)
    baseClass(PrecompiledProjectScript::class)
    implicitReceivers(Project::class)
    defaultImportsForPrecompiledScript()
})


private
fun ScriptCompilationConfiguration.Builder.defaultImportsForPrecompiledScript() {
    refineConfiguration {
        beforeCompiling { context ->
            val environment = scriptResolverEnvironmentOf(context)
            require(environment != null)
            context.compilationConfiguration.with {
                defaultImports(
                    PrecompiledScriptDependenciesResolver.implicitImportsForScript(
                        context.script.text,
                        environment
                    )
                )
            }.asSuccess()
        }
    }
}


private
fun scriptResolverEnvironmentOf(context: ScriptConfigurationRefinementContext): Environment? =
    context
        .compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
        ?.get(ScriptingHostConfiguration.getEnvironment)
        ?.invoke()


private
object NullPluginDependencySpec : PluginDependencySpec {
    override fun apply(apply: Boolean) = this
    override fun version(version: String?) = this
}
