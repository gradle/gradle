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

import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.KotlinSettingsScript

import org.gradle.kotlin.dsl.support.KotlinBuildscriptBlock
import org.gradle.kotlin.dsl.support.KotlinPluginsBlock
import org.gradle.kotlin.dsl.support.KotlinSettingsBuildscriptBlock

import org.gradle.kotlin.dsl.accessors.AccessorsClassPath
import org.gradle.kotlin.dsl.accessors.accessorsClassPathFor

import java.io.File
import java.lang.IllegalArgumentException

import kotlin.reflect.KClass


internal
fun kotlinScriptTargetFor(target: Any, topLevelScript: Boolean): KotlinScriptTarget<out Any> =
    when (target) {
        is Project  -> projectScriptTarget(target, topLevelScript)
        is Settings -> settingsScriptTarget(target, topLevelScript)
        else        -> unsupportedTarget(target)
    }


private
fun unsupportedTarget(target: Any): Nothing =
    throw IllegalArgumentException("Unsupported target ${target::class.qualifiedName}: $target")


private
fun settingsScriptTarget(settings: Settings, topLevelScript: Boolean) =
    KotlinScriptTarget(
        settings,
        type = Settings::class,
        scriptTemplate = KotlinSettingsScript::class,
        buildscriptBlockTemplate = KotlinSettingsBuildscriptBlock::class.takeIf { topLevelScript },
        rootDir = settings.rootDir)


private
fun projectScriptTarget(project: Project, topLevelScript: Boolean): KotlinScriptTarget<Project> =
    KotlinScriptTarget(
        project,
        type = Project::class,
        scriptTemplate = KotlinBuildScript::class,
        buildscriptBlockTemplate = KotlinBuildscriptBlock::class.takeIf { topLevelScript },
        pluginsBlockTemplate = KotlinPluginsBlock::class.takeIf { topLevelScript },
        rootDir = project.rootDir,
        accessorsClassPath = accessorsClassPathProviderFor(project, topLevelScript),
        prepare = {
            project.run {
                afterEvaluate {
                    plugins.apply(KotlinScriptBasePlugin::class.java)
                }
            }
        })


private
fun accessorsClassPathProviderFor(project: Project, topLevelScript: Boolean): AccessorsClassPathProvider =
    if (topLevelScript) { classPath -> accessorsClassPathFor(project, classPath) }
    else emptyAccessorsClassPathProvider


internal
typealias AccessorsClassPathProvider = (ClassPath) -> AccessorsClassPath


private
val emptyAccessorsClassPathProvider: AccessorsClassPathProvider = { AccessorsClassPath.empty }


internal
data class KotlinScriptTarget<T : Any>(
    val `object`: T,
    val type: KClass<T>,
    val scriptTemplate: KClass<*>,
    val buildscriptBlockTemplate: KClass<*>?,
    val pluginsBlockTemplate: KClass<*>? = null,
    val rootDir: File,
    val accessorsClassPath: AccessorsClassPathProvider = emptyAccessorsClassPathProvider,
    val prepare: () -> Unit = {}) {

    fun accessorsClassPathFor(classPath: ClassPath) = accessorsClassPath(classPath)
}
