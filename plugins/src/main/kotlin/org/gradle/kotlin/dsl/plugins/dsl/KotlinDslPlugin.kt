/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.gradleKotlinDsl
import org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin

import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension
import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverGradleSubplugin


/**
 * The `kotlin-dsl` plugin.
 *
 * - Applies the `embedded-kotlin` plugin
 * - Adds the `gradleKotlinDsl()` dependency to the `compileOnly` and `testRuntimeOnly` configurations
 * - Configures the Kotlin DSL compiler plugins
 *
 * @see org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin
 */
open class KotlinDslPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.run {

            applyEmbeddedKotlinPlugin()
            addGradleKotlinDslDependencyTo("compileOnly", "testRuntimeOnly")
            configureCompilerPlugins()
        }
    }

    private
    fun Project.applyEmbeddedKotlinPlugin() {
        plugins.apply(EmbeddedKotlinPlugin::class.java)
    }

    private
    fun Project.addGradleKotlinDslDependencyTo(vararg configurations: String) {
        configurations.forEach {
            dependencies.add(it, gradleKotlinDsl())
        }
    }

    private
    fun Project.configureCompilerPlugins() {
        plugins.apply(SamWithReceiverGradleSubplugin::class.java)
        extensions.configure(SamWithReceiverExtension::class.java) { samWithReceiver ->
            samWithReceiver.annotation(org.gradle.api.HasImplicitReceiver::class.qualifiedName!!)
        }
    }
}
