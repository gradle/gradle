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
package org.gradle.kotlin.dsl.plugins.embedded

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.logging.Logger
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject


/**
 * The `embedded-kotlin` plugin.
 *
 * Applies the `org.jetbrains.kotlin.jvm` plugin,
 * adds compile only and test implementation dependencies on `kotlin-stdlib` and `kotlin-reflect`.
 */
abstract class EmbeddedKotlinPlugin @Inject internal constructor(
    private val embeddedKotlin: EmbeddedKotlinProvider
) : Plugin<Project> {

    override fun apply(project: Project) {
        project.run {

            plugins.apply(KotlinPluginWrapper::class.java)

            warnOnDifferentKotlinCompilerVersion()

            val embeddedKotlinConfiguration = configurations.create("embeddedKotlin")
            embeddedKotlin.addDependenciesTo(
                dependencies,
                embeddedKotlinConfiguration.name,
                "stdlib", "reflect"
            )

            kotlinArtifactConfigurationNames.forEach {
                configurations.getByName(it).extendsFrom(embeddedKotlinConfiguration)
            }

            workAroundKgpEagerConfigurations()
        }
    }

    /**
     * A read of `compilerVersion` finalizes the value, so the read waits for the task configuration.
     * A project can have several Kotlin compile tasks, and the warning appears one time.
     */
    private fun Project.warnOnDifferentKotlinCompilerVersion() {
        var warned = false
        tasks.withType<KotlinCompile>().configureEach {
            if (!warned) {
                warned = true
                logger.warnOnDifferentKotlinVersion(kotlinCompilerVersion())
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalBuildToolsApi::class)
    private fun Project.kotlinCompilerVersion(): String =
        kotlinExtension.compilerVersion.orNull ?: getKotlinPluginVersion()

    // See https://github.com/gradle/gradle/issues/35309
    // TODO remove once https://youtrack.jetbrains.com/issue/KT-81706/ is fixed
    private fun Project.workAroundKgpEagerConfigurations() {
        afterEvaluate {
            configurations.named { name -> name == "swiftExportClasspath" }.configureEach { swift ->
                swift.withDependencies { dependencies ->
                    dependencies.clear()
                }
            }
        }
    }
}


fun Logger.warnOnDifferentKotlinVersion(kotlinCompilerVersion: String?) {
    if (kotlinCompilerVersion != embeddedKotlinVersion) {
        val warning =
            """|WARNING: Unsupported Kotlin compiler version.
               |The `embedded-kotlin` and `kotlin-dsl` plugins rely on features of Kotlin `$embeddedKotlinVersion` that might work differently than in the compiler version `$kotlinCompilerVersion`.
               |Using the `kotlin-dsl` plugin together with a different Kotlin version (for example, by using the Kotlin Gradle plugin (`kotlin(jvm)`)) in the same project is not recommended.
               |
               |See https://docs.gradle.org/${GradleVersion.current().version}/userguide/kotlin_dsl.html#sec:kotlin-dsl_plugin for more details on how the `kotlin-dsl` plugin works (same applies to `embedded-kotlin`).
               |It applies a certain version of the `org.jetbrains.kotlin.jvm` plugin and also add a dependency on the Kotlin Standard Library.
               |
               |Applying other version of the `org.jetbrains.kotlin.jvm` plugin in the build and/or adding dependencies to different versions of the Kotlin Standard Library can cause incompatibilities.
               |See https://docs.gradle.org/${GradleVersion.current().version}/userguide/kotlin_dsl.html#sec:kotlin""".trimMargin()
        warn(warning)
    }
}


internal
val kotlinArtifactConfigurationNames =
    listOf(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME, JvmConstants.TEST_IMPLEMENTATION_CONFIGURATION_NAME)
