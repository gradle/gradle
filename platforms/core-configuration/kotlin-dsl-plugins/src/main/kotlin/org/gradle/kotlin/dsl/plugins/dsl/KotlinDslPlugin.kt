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
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.plugins.appliedKotlinDslPluginsVersion
import org.gradle.kotlin.dsl.plugins.base.KotlinDslBasePlugin
import org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin


/**
 * The `kotlin-dsl` plugin.
 *
 * - Applies the `java-gradle-plugin` plugin
 * - Applies the `kotlin-dsl.base` plugin
 * - Applies the `kotlin-dsl.precompiled-script-plugins` plugin
 *
 * @see JavaGradlePluginPlugin
 * @see KotlinDslBasePlugin
 * @see PrecompiledScriptPlugins
 *
 * @see <a href="https://docs.gradle.org/current/userguide/kotlin_dsl.html">Kotlin DSL reference</a>
 */
abstract class KotlinDslPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        warnOnUnexpectedKotlinDslPluginVersion()

        apply<JavaGradlePluginPlugin>()
        apply<KotlinDslBasePlugin>()
        apply<PrecompiledScriptPlugins>()
    }

    private
    fun Project.warnOnUnexpectedKotlinDslPluginVersion() {
        if (expectedKotlinDslPluginsVersion != appliedKotlinDslPluginsVersion) {
            logger.warn(
                "This version of Gradle expects version '{}' of the `kotlin-dsl` plugin but version '{}' has been applied to {}. " +
                    "Let Gradle control the version of `kotlin-dsl` by removing any explicit `kotlin-dsl` version constraints from your build logic.",
                expectedKotlinDslPluginsVersion, appliedKotlinDslPluginsVersion, project
            )
        }
    }
}
