/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.HasImplicitReceiver
import org.gradle.api.Project
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.plugins.PluginAware
import org.gradle.kotlin.dsl.precompile.v1.scriptResolverEnvironmentOf
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.DefaultKotlinScript
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.defaultKotlinScriptHostForProject
import org.gradle.kotlin.dsl.support.internalError
import org.gradle.kotlin.dsl.support.invalidPluginsCall
import org.gradle.plugin.use.PluginDependenciesSpec
import org.jetbrains.kotlin.scripting.definitions.annotationsForSamWithReceivers
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptDependenciesResolver
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ExternalSourceCode
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath


/**
 * Base class for Kotlin build scripts.
 */
@GradleDsl
@KotlinScript(
    displayName = "Gradle Project Script",
    fileExtension = "gradle.kts",
    filePathPattern = ".*\\.gradle\\.kts",
    compilationConfiguration = KotlinBuildScriptCompilationConfiguration::class
)
open class KotlinBuildScript(
    private val host: KotlinScriptHost<Project>
) : DefaultKotlinScript(defaultKotlinScriptHostForProject(host.target)), PluginAware by host.target {

    /**
     * The [ScriptHandler] for this script.
     */
    val buildscript: ScriptHandler
        get() = host.scriptHandler

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit): Unit =
        internalError()

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    open fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpec.() -> Unit): Unit =
        invalidPluginsCall()
}


/**
 * @since 6.0
 */
internal
object KotlinBuildScriptCompilationConfiguration : ScriptCompilationConfiguration({

    implicitReceivers(
        org.gradle.api.Project::class
    )

    compilerOptions(
        "-jvm-target", "1.8",
        "-Xjsr305=strict",
        "-XXLanguage:+NewInference",
        "-XXLanguage:+SamConversionForKotlinFunctions"
    )

    defaultImports(
        "org.gradle.kotlin.dsl.*",
        "org.gradle.api.*"
    )

    jvm {
        dependenciesFromClassContext(
            KotlinBuildScriptCompilationConfiguration::class,
            "gradle-kotlin-dsl",
            "gradle-kotlin-dsl-extensions",
            "gradle-kotlin-dsl-tooling-models",
            "gradle-api", "groovy-all",
            "kotlin-stdlib", "kotlin-reflect"
        )
    }

    annotationsForSamWithReceivers(
        HasImplicitReceiver::class
    )

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

    refineConfiguration {
        beforeCompiling { context ->

            val scriptContents = object : ScriptContents {
                override val annotations: Iterable<Annotation>
                    get() = emptyList()
                override val file: File?
                    get() = (context.script as? ExternalSourceCode)?.externalLocation?.toURI()?.let(::File)
                override val text: CharSequence?
                    get() = context.script.text
            }

            val environment = scriptResolverEnvironmentOf(context)

            val report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit =
                { severity, message, position ->
                    // TODO: accumulate diagnostics
                }

            val result =
                KotlinBuildScriptDependenciesResolver().run {
                    resolve(
                        scriptContents,
                        environment,
                        report,
                        null
                    )
                }.get(3, TimeUnit.MINUTES)

            context.compilationConfiguration.with {
                result?.apply {
                    updateClasspath(classpath.toList())
                    defaultImports(imports.toList())
                }
            }.asSuccess()
        }
    }
})
