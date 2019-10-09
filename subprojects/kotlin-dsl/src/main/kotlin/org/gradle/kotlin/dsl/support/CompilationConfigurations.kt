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

package org.gradle.kotlin.dsl.support

import org.gradle.api.HasImplicitReceiver
import org.gradle.kotlin.dsl.precompile.v1.scriptResolverEnvironmentOf
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.jetbrains.kotlin.scripting.definitions.annotationsForSamWithReceivers
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptDependenciesResolver
import kotlin.script.experimental.api.ExternalSourceCode
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
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
            refineKotlinScriptConfiguration(context)
        }
    }
})


private
fun refineKotlinScriptConfiguration(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics.Success<ScriptCompilationConfiguration> {
    val script = context.script

    val diagnostics = mutableListOf<ScriptDiagnostic>()

    val scriptExternalDependencies = KotlinBuildScriptDependenciesResolver().run {
        resolve(
            scriptContentsOf(script),
            scriptResolverEnvironmentOf(context),
            { severity, message, pos -> diagnostics.add(scriptDiagnosticOf(script, message, severity, pos)) },
            null
        )
    }.get()

    return context.compilationConfiguration.with {
        scriptExternalDependencies?.apply {
            updateClasspath(classpath.toList())
            defaultImports(imports.toList())
        }
    }.asSuccess(diagnostics)
}


private
fun scriptContentsOf(script: SourceCode) =
    object : ScriptContents {
        override val annotations: Iterable<Annotation>
            get() = emptyList()
        override val file: File?
            get() = (script as? ExternalSourceCode)?.externalLocation?.toURI()?.let(::File)
        override val text: CharSequence?
            get() = script.text
    }


private
fun scriptDiagnosticOf(
    script: SourceCode,
    message: String,
    severity: ScriptDependenciesResolver.ReportSeverity,
    position: ScriptContents.Position?
) = ScriptDiagnostic(
    message,
    ScriptDiagnostic.Severity.values()[severity.ordinal],
    script.name,
    position?.run {
        SourceCode.Location(
            SourceCode.Position(line, col)
        )
    }
)
