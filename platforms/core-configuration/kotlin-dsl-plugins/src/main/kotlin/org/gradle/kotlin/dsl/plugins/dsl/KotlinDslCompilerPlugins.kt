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
import org.gradle.api.SupportsKotlinAssignmentOverloading
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.logging.slf4j.ContextAwareTaskLogger
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.provider.KotlinDslPluginSupport
import org.jetbrains.kotlin.assignment.plugin.gradle.AssignmentExtension
import org.jetbrains.kotlin.assignment.plugin.gradle.AssignmentSubplugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension
import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverGradleSubplugin


/**
 * Configures the Kotlin compiler to recognise Gradle functional interface
 * annotated with [HasImplicitReceiver].
 */
abstract class KotlinDslCompilerPlugins : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        plugins.apply(SamWithReceiverGradleSubplugin::class.java)
        extensions.configure(SamWithReceiverExtension::class.java) { samWithReceiver ->
            samWithReceiver.annotation(HasImplicitReceiver::class.qualifiedName!!)
        }

        plugins.apply(AssignmentSubplugin::class.java)
        extensions.configure(AssignmentExtension::class.java) { assignment ->
            assignment.annotation(SupportsKotlinAssignmentOverloading::class.qualifiedName!!)
        }

        kotlinDslPluginOptions {
            tasks.withType<KotlinCompile>().configureEach { kotlinCompile ->
                kotlinCompile.compilerOptions {
                    DeprecationLogger.whileDisabled {
                        @Suppress("DEPRECATION")
                        if (this@kotlinDslPluginOptions.jvmTarget.isPresent) {
                            jvmTarget.set(this@kotlinDslPluginOptions.jvmTarget.map { JvmTarget.fromTarget(it) })
                        }
                    }
                    apiVersion.set(KotlinVersion.KOTLIN_1_8)
                    languageVersion.set(KotlinVersion.KOTLIN_1_8)
                    freeCompilerArgs.addAll(KotlinDslPluginSupport.kotlinCompilerArgs)
                }
                kotlinCompile.setWarningRewriter(ExperimentalCompilerWarningSilencer(listOf(
                    "-XXLanguage:+DisableCompatibilityModeForNewInference",
                    "-XXLanguage:-TypeEnhancementImprovementsInStrictMode",
                )))
            }
        }
    }

    private
    fun KotlinCompile.setWarningRewriter(rewriter: ContextAwareTaskLogger.MessageRewriter) {
        (logger as ContextAwareTaskLogger).setMessageRewriter(rewriter)
    }
}
