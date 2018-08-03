/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.api.HasImplicitReceiver
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import org.gradle.api.internal.TaskInternal

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension
import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverGradleSubplugin

import org.gradle.kotlin.dsl.*


/**
 * Configures the Kotlin compiler to recognise Gradle functional interface
 * annotated with [HasImplicitReceiver].
 */
class KotlinDslCompilerPlugins : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        plugins.apply(SamWithReceiverGradleSubplugin::class.java)
        extensions.configure(SamWithReceiverExtension::class.java) { samWithReceiver ->
            samWithReceiver.annotation(HasImplicitReceiver::class.qualifiedName!!)
        }

        afterEvaluate {
            kotlinDslPluginOptions {
                tasks.withType<KotlinCompile>().configureEach {
                    it.kotlinOptions {
                        jvmTarget = this@kotlinDslPluginOptions.jvmTarget.get()
                        freeCompilerArgs += listOf("-java-parameters", "-Xuse-old-class-files-reading")
                    }
                    it.applyKotlinDslPluginProgressiveMode(progressive.get())
                }
            }
        }
    }
}


private
fun KotlinCompile.applyKotlinDslPluginProgressiveMode(progressiveModeState: ProgressiveModeState) {
    when (progressiveModeState) {
        ProgressiveModeState.WARN -> {
            enableSamConversionForKotlinFunctions()
            replaceLoggerWith(KotlinCompilerWarningSubstitutingLogger(logger))
        }
        ProgressiveModeState.ENABLED -> {
            enableSamConversionForKotlinFunctions()
            replaceLoggerWith(KotlinCompilerWarningSilencingLogger(logger))
        }
        ProgressiveModeState.DISABLED -> {
            // NOOP
        }
    }
}


private
fun KotlinCompile.enableSamConversionForKotlinFunctions() {
    kotlinOptions {
        freeCompilerArgs += listOf(
            KotlinCompilerArguments.progressive,
            KotlinCompilerArguments.newInference,
            KotlinCompilerArguments.samConversionForKotlinFunctions
        )
    }
}


internal
object KotlinCompilerArguments {
    const val progressive = "-Xprogressive"
    const val newInference = "-XXLanguage:+NewInference"
    const val samConversionForKotlinFunctions = "-XXLanguage:+SamConversionForKotlinFunctions"
}


private
fun KotlinCompile.replaceLoggerWith(logger: Logger) {
    (this as TaskInternal).replaceLogger(logger)
}


private
class KotlinCompilerWarningSubstitutingLogger(private val delegate: Logger) : Logger by delegate {
    override fun warn(message: String) {
        if (message.contains(KotlinCompilerArguments.samConversionForKotlinFunctions)) delegate.warn(kotlinDslPluginProgressiveWarning)
        else delegate.warn(message)
    }
}


private
class KotlinCompilerWarningSilencingLogger(private val delegate: Logger) : Logger by delegate {
    override fun warn(message: String) {
        if (!message.contains(KotlinCompilerArguments.samConversionForKotlinFunctions)) {
            delegate.warn(message)
        }
    }
}


internal
val kotlinDslPluginProgressiveWarning = """
WARNING
The `kotlin-dsl` plugin relies on Kotlin compiler's progressive mode and experimental features to enable among other things SAM conversion for Kotlin functions.

Once built and published, artifacts produced by this project will continue to work on future Gradle versions.
However, you may have to fix the sources of this project after upgrading the Gradle wrapper of this build.

To silence this warning add the following to this project's build script:

    kotlinDslPluginOptions.progressive.set(ProgressiveModeState.ENABLED)

To disable Gradle Kotlin DSL progressive mode and give up SAM conversion for Kotlin functions add the following to this project's build script:

    kotlinDslPluginOptions.progressive.set(ProgressiveModeState.DISABLED)

See the `progressive` property documentation for more information.
""".trimIndent()
