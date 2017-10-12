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
package org.gradle.kotlin.dsl.provider

import org.gradle.api.Project
import org.gradle.api.initialization.Settings

import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.KotlinSettingsScript

import org.gradle.kotlin.dsl.accessors.AccessorsClassPath
import org.gradle.kotlin.dsl.accessors.accessorsClassPathFor

import java.io.File
import java.lang.IllegalArgumentException

import kotlin.reflect.KClass


internal
fun kotlinScriptPluginTargetFor(target: Any): KotlinScriptPluginTarget<*> =
    when (target) {
        is Project  -> KotlinScriptPluginProjectTarget(target)
        is Settings -> KotlinScriptPluginSettingsTarget(target)
        else        -> unsupportedTarget(target)
    }


internal
fun unsupportedTarget(target: Any): Nothing =
    throw IllegalArgumentException("Unsupported target ${target::class.qualifiedName}: $target")


internal
sealed class KotlinScriptPluginTarget<T : Any>(
    open val `object`: T,
    val targetType: KClass<T>,
    val scriptTemplate: KClass<*>,
    val rootDir: File,
    val gradleUserHomeDir: File,
    val gradleHomeDir: File?,
    val logDir: File,
    val supportsBuildscriptBlock: Boolean = false,
    val supportsPluginsBlock: Boolean = false,
    val pluginManager: PluginManagerInternal? = null,
    val providesAccessors: Boolean = false) {

    open fun accessorsClassPath(classPath: ClassPath) =
        AccessorsClassPath(ClassPath.EMPTY, ClassPath.EMPTY)
}

internal
data class KotlinScriptPluginProjectTarget(override val `object`: Project) : KotlinScriptPluginTarget<Project>(
    `object`,
    targetType = Project::class,
    scriptTemplate = KotlinBuildScript::class,
    rootDir = `object`.rootDir,
    gradleUserHomeDir = `object`.gradle.gradleUserHomeDir,
    gradleHomeDir = `object`.gradle.gradleHomeDir,
    logDir = `object`.buildDir,
    supportsBuildscriptBlock = true,
    supportsPluginsBlock = true,
    pluginManager = (`object` as ProjectInternal).pluginManager,
    providesAccessors = true) {

    override fun accessorsClassPath(classPath: ClassPath) =
        accessorsClassPathFor(`object`, classPath)
}

internal
data class KotlinScriptPluginSettingsTarget(override val `object`: Settings) : KotlinScriptPluginTarget<Settings>(
    `object`,
    targetType = Settings::class,
    scriptTemplate = KotlinSettingsScript::class,
    rootDir = `object`.rootDir,
    gradleUserHomeDir = `object`.gradle.gradleUserHomeDir,
    gradleHomeDir = `object`.gradle.gradleHomeDir,
    logDir = File(`object`.rootDir, "build"))
