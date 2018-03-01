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
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.KotlinInitScript
import org.gradle.kotlin.dsl.KotlinSettingsScript
import org.gradle.kotlin.dsl.accessors.AccessorsClassPath
import org.gradle.kotlin.dsl.accessors.accessorsClassPathFor
import org.gradle.kotlin.dsl.support.*

import java.lang.IllegalArgumentException

import kotlin.reflect.KClass


internal
fun kotlinScriptTargetFor(
    target: Any,
    scriptSource: ScriptSource,
    scriptHandler: ScriptHandler,
    baseScope: ClassLoaderScope,
    topLevelScript: Boolean): KotlinScriptTarget<out Any> =

    when (target) {
        is Project  -> projectScriptTarget(target, scriptSource, scriptHandler, baseScope, topLevelScript)
        is Settings -> settingsScriptTarget(target, scriptSource, scriptHandler, baseScope, topLevelScript)
        is Gradle   -> gradleInitScriptTarget(target, scriptHandler, scriptSource, baseScope)
        else        -> unsupportedTarget(target)
    }


private
fun unsupportedTarget(target: Any): Nothing =
    throw IllegalArgumentException("Unsupported target ${target::class.qualifiedName}: $target")


private
fun settingsScriptTarget(
    settings: Settings,
    scriptSource: ScriptSource,
    scriptHandler: ScriptHandler,
    baseScope: ClassLoaderScope,
    topLevelScript: Boolean) =

    KotlinScriptTarget(
        settings,
        type = Settings::class,
        scriptHandler = scriptHandler,
        scriptTemplate = KotlinSettingsScript::class,
        buildscriptBlockTemplate = KotlinSettingsBuildscriptBlock::class.takeIf { topLevelScript },
        host = lazy { KotlinScriptHost(settings, scriptSource, (settings.gradle as GradleInternal).services, baseScope, scriptHandler) })


private
fun projectScriptTarget(
    project: Project,
    scriptSource: ScriptSource,
    scriptHandler: ScriptHandler,
    baseScope: ClassLoaderScope,
    topLevelScript: Boolean): KotlinScriptTarget<Project> =

    KotlinScriptTarget(
        project,
        type = Project::class,
        scriptHandler = scriptHandler,
        scriptTemplate = KotlinBuildScript::class,
        buildscriptBlockTemplate = KotlinBuildscriptBlock::class.takeIf { topLevelScript },
        pluginsBlockTemplate = KotlinPluginsBlock::class.takeIf { topLevelScript },
        accessorsClassPath = accessorsClassPathProviderFor(project, topLevelScript),
        host = lazy { KotlinScriptHost(project, scriptSource, (project as ProjectInternal).services, baseScope, scriptHandler) },
        prepare = {
            project.run {
                afterEvaluate {
                    plugins.apply(KotlinScriptBasePlugin::class.java)
                }
            }
        })


internal
fun gradleInitScriptTarget(
    gradle: Gradle,
    scriptHandler: ScriptHandler,
    scriptSource: ScriptSource,
    baseScope: ClassLoaderScope): KotlinScriptTarget<Gradle> =

    KotlinScriptTarget(
        gradle,
        type = Gradle::class,
        scriptHandler = scriptHandler,
        scriptTemplate = KotlinInitScript::class,
        buildscriptBlockTemplate = KotlinInitscriptBlock::class,
        buildscriptBlockName = "initscript",
        host = lazy { KotlinScriptHost(gradle, scriptSource, (gradle as GradleInternal).services, baseScope, scriptHandler) })


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
    val scriptHandler: ScriptHandler,
    val scriptTemplate: KClass<*>,
    val buildscriptBlockTemplate: KClass<*>?,
    val host: Lazy<KotlinScriptHost>,
    val pluginsBlockTemplate: KClass<*>? = null,
    val accessorsClassPath: AccessorsClassPathProvider = emptyAccessorsClassPathProvider,
    val prepare: () -> Unit = {},
    val buildscriptBlockName: String = "buildscript",
    val eval: (Class<*>) -> Unit = { scriptClass ->
        scriptClass
            .getConstructor(KotlinScriptHost::class.java, type.java)
            .newInstance(host.value, `object`)
    },
    val evalBuildscriptBlock: (Class<*>) -> Unit = { scriptClass ->
        scriptClass
            .getConstructor(KotlinScriptHost::class.java, type.java)
            .newInstance(host.value, `object`)
    }) {

    fun accessorsClassPathFor(classPath: ClassPath) = accessorsClassPath(classPath)
}
