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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.TaskInternal
import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.slf4j.ContextAwareTaskLogger

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension
import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverGradleSubplugin

import org.gradle.kotlin.dsl.*

import org.gradle.kotlin.dsl.support.serviceOf


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
                        apiVersion = "1.4"
                        languageVersion = "1.4"
                        freeCompilerArgs += listOf(
                            KotlinCompilerArguments.javaParameters,
                            KotlinCompilerArguments.jsr305Strict,
                            KotlinCompilerArguments.samConversionForKotlinFunctions,
                            KotlinCompilerArguments.referencesToSyntheticJavaProperties
                        )
                    }
                    it.applyExperimentalWarning(experimentalWarning.get())
                }
            }
        }
    }
}


object KotlinCompilerArguments {
    const val javaParameters = "-java-parameters"
    const val jsr305Strict = "-Xjsr305=strict"
    const val samConversionForKotlinFunctions = "-XXLanguage:+SamConversionForKotlinFunctions"
    const val referencesToSyntheticJavaProperties = "-XXLanguage:+ReferencesToSyntheticJavaProperties"
}


private
fun KotlinCompile.applyExperimentalWarning(experimentalWarning: Boolean) {
    setWarningRewriter(newLoggerMessageRewriterFor(experimentalWarning, project.toString(), project.experimentalWarningLink))
}


private
fun KotlinCompile.setWarningRewriter(rewriter: ContextAwareTaskLogger.MessageRewriter) {
    (this as TaskInternal).setLoggerMessageRewriter(rewriter)
}


private
fun newLoggerMessageRewriterFor(experimentalWarning: Boolean, target: String, link: String) =
    { logLevel: LogLevel, message: String ->
        when {
            logLevel != LogLevel.WARN -> message
            !message.contains(KotlinCompilerArguments.samConversionForKotlinFunctions) -> message
            experimentalWarning -> kotlinDslPluginExperimentalWarning(target, link)
            else -> null
        }
    }


fun kotlinDslPluginExperimentalWarning(target: String, link: String) =
    "The `kotlin-dsl` plugin applied to $target enables experimental Kotlin compiler features. For more information see $link"


private
val Project.experimentalWarningLink
    get() = documentationRegistry.getDocumentationFor("kotlin_dsl", "sec:kotlin-dsl_plugin")


private
val Project.documentationRegistry
    get() = serviceOf<DocumentationRegistry>()
