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

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.Factory
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.plugins.appliedKotlinDslPluginsVersion
import org.gradle.kotlin.dsl.plugins.base.KotlinDslBasePlugin
import org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject


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

        afterEvaluate {
            workaroundKotlinJavaVersionSupport()
        }
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

    private
    fun Project.workaroundKotlinJavaVersionSupport() {
        val targetJavaVersion = kotlinDslOptionsJvmTarget
            .orElse(defaultToolchainJavaVersion)
            .map(::toLastSupportedKotlinJavaVersion)
        tasks.withType(KotlinCompile::class.java).configureEach { kotlinCompile ->
            kotlinCompile.compilerOptions.jvmTarget.set(
                targetJavaVersion.map { JvmTarget.fromTarget(it.toString()) }
            )
        }
        tasks.withType(JavaCompile::class.java).configureEach { javaCompile ->
            javaCompile.targetCompatibility = targetJavaVersion.get().toString()
        }
    }

    private
    val Project.kotlinDslOptionsJvmTarget: Provider<JavaVersion>
        get() = DeprecationLogger.whileDisabled(Factory {
            @Suppress("DEPRECATION")
            extensions.getByType(KotlinDslPluginOptions::class.java).jvmTarget.map { JavaVersion.toVersion(it) }
        })!!

    private
    val Project.defaultToolchainJavaVersion: Provider<JavaVersion>
        get() = toolchains.launcherFor(java.toolchain).map {
            JavaVersion.toVersion(it.metadata.languageVersion.asInt())
        }

    private
    fun toLastSupportedKotlinJavaVersion(javaVersion: JavaVersion): JavaVersion =
        javaVersion.takeIf { it <= lastSupportedJvmKotlinTarget } ?: lastSupportedJvmKotlinTarget

    private
    val lastSupportedJvmKotlinTarget: JavaVersion
        get() = JavaVersion.toVersion(JvmTarget.values().last().target)

    private
    val Project.java: JavaPluginExtension
        get() = extensions.getByType(JavaPluginExtension::class.java)

    @get:Inject
    protected
    abstract val toolchains: JavaToolchainService
}
