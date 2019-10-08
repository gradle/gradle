package org.gradle.kotlin.dsl

import org.gradle.api.HasImplicitReceiver
import org.gradle.api.Incubating
import org.jetbrains.kotlin.scripting.definitions.annotationsForSamWithReceivers
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm


/**
 * @since 6.0
 */
@KotlinScript(
    displayName = "My Kotlin Script",
    fileExtension = "my.kts",
    filePathPattern = ".*\\.my\\.kts",
    compilationConfiguration = MyKotlinScriptCompilationConfiguration::class
)
@Incubating
open class MyKotlinScript {

    fun myKotlinScriptFunction() = println("42!")
}


/**
 * @since 6.0
 */
@Incubating
object MyKotlinScriptCompilationConfiguration : ScriptCompilationConfiguration({

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
            MyKotlinScriptCompilationConfiguration::class,
            "gradle-kotlin-dsl",
            "gradle-kotlin-dsl-extensions",
            "gradle-api",
            "kotlin-stdlib", "kotlin-reflect"
        )
    }

    annotationsForSamWithReceivers(
        HasImplicitReceiver::class
    )

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
})
