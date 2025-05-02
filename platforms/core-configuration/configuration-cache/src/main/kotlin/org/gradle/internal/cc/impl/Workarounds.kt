/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.TaskInternal
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters


internal
object Workarounds {

    // TODO:[DefaultBuildTreeLifecycleController.isEligibleToRunTasks()] remove once fixed

    private
    val ignoredStartParameterProperties = arrayOf(
        KotlinDslModelsParameters.CORRELATION_ID_GRADLE_PROPERTY_NAME // Changing by IDE
    )

    fun isIgnoredStartParameterProperty(key: String): Boolean =
        ignoredStartParameterProperties.contains(key)


    fun canReadSystemProperty(from: String): Boolean =
        withWorkaroundsFor("systemProps") {
            isBuildScanPlugin(from)
        }

    fun canReadEnvironmentVariable(from: String): Boolean =
        withWorkaroundsFor("envVars") {
            isBuildScanPlugin(from)
        }

    fun canStartExternalProcesses(from: String): Boolean =
        withWorkaroundsFor("processes") {
            isBuildScanPlugin(from)
        }

    fun canReadFiles(from: String): Boolean =
        withWorkaroundsFor("files") {
            isBuildScanPlugin(from)
        }

    fun canAccessProjectAtExecutionTime(task: TaskInternal) =
        withWorkaroundsFor("task-project") {
            task.javaClass.name.run {
                startsWith("com.android.build.gradle.tasks.ShaderCompile")
                    || startsWith("com.android.build.gradle.tasks.MapSourceSetPathsTask")
                    || startsWith("com.android.build.gradle.tasks.MergeResources")
            }
        }

    fun canAccessConventions(from: String, area: String) =
        withWorkaroundsFor(area) {
            from.startsWith("com.android.build.gradle.tasks.factory.AndroidUnitTest") || callStackHasElement {
                isBuildScanPlugin(className)
            }
        }

    private
    inline fun callStackHasElement(stackElementMatcher: StackTraceElement.() -> Boolean) = Thread.currentThread().stackTrace.any(stackElementMatcher)

    private
    fun isBuildScanPlugin(from: String): Boolean = from.run {
        startsWith("com.gradle.scan.plugin.internal.")
            || startsWith("com.gradle.enterprise.agent.")
            || startsWith("com.gradle.enterprise.gradleplugin.testacceleration.")
    }

    private
    inline fun withWorkaroundsFor(area: String, isEnabled: () -> Boolean): Boolean =
        isEnabled() && !shouldDisableInputWorkaroundsFor(area)

    private
    fun shouldDisableInputWorkaroundsFor(area: String): Boolean =
        System.getProperty("org.gradle.internal.disable.input.workarounds")?.contains(area, ignoreCase = true) ?: false
}
