/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution.initialization

import org.gradle.StartParameter
import org.gradle.initialization.layout.BuildLayout
import org.gradle.instantexecution.SystemProperties
import org.gradle.instantexecution.extensions.unsafeLazy
import org.gradle.internal.hash.HashUtil.createCompactMD5
import org.gradle.util.GFileUtils
import java.io.File


class InstantExecutionStartParameter(
    private val buildLayout: BuildLayout,
    private val startParameter: StartParameter
) {

    val isEnabled: Boolean by unsafeLazy {
        systemPropertyFlag(SystemProperties.isEnabled)
    }

    val isQuiet: Boolean
        get() = systemPropertyFlag(SystemProperties.isQuiet)

    val maxProblems: Int by unsafeLazy {
        systemProperty(SystemProperties.maxProblems)
            ?.let(Integer::valueOf)
            ?: 512
    }

    val failOnProblems: Boolean by unsafeLazy {
        systemPropertyFlag(SystemProperties.failOnProblems, true)
    }

    val recreateCache: Boolean
        get() = systemPropertyFlag(SystemProperties.recreateCache)

    val settingsDirectory: File
        get() = buildLayout.settingsDir

    val rootDirectory: File
        get() = buildLayout.rootDirectory

    val isRefreshDependencies
        get() = startParameter.isRefreshDependencies

    val requestedTaskNames: List<String> by unsafeLazy {
        startParameter.taskNames
    }

    val instantExecutionCacheKey: String by unsafeLazy {
        // The following characters are not valid in task names
        // and can be used as separators: /, \, :, <, >, ", ?, *, |
        // except we also accept qualified task names with :, so colon is out.
        val cacheKey = StringBuilder()
        requestedTaskNames.joinTo(cacheKey, separator = "/")
        val excludedTaskNames = startParameter.excludedTaskNames
        if (excludedTaskNames.isNotEmpty()) {
            excludedTaskNames.joinTo(cacheKey, prefix = "<", separator = "/")
        }
        val taskNames = requestedTaskNames.asSequence() + excludedTaskNames.asSequence()
        val hasRelativeTaskName = taskNames.any { !it.startsWith(':') }
        if (hasRelativeTaskName) {
            // Because unqualified task names are resolved relative to the enclosing
            // sub-project according to `invocationDirectory`,
            // the relative invocation directory information must be part of the key.
            relativeChildPathOrNull(startParameter.currentDir, rootDirectory)?.let { relativeSubDir ->
                cacheKey.append('*')
                cacheKey.append(relativeSubDir)
            }
        }
        createCompactMD5(cacheKey.toString())
    }

    /**
     * Returns the path of [target] relative to [base] if
     * [target] is a child of [base] or `null` otherwise.
     */
    private
    fun relativeChildPathOrNull(target: File, base: File): String? =
        GFileUtils.relativePathOf(target, base)
            .takeIf { !it.startsWith('.') }

    private
    fun systemPropertyFlag(propertyName: String, defaultValue: Boolean = false): Boolean =
        systemProperty(propertyName)?.toBoolean() ?: defaultValue

    private
    fun systemProperty(propertyName: String) =
        startParameter.systemPropertiesArgs[propertyName] ?: System.getProperty(propertyName)
}
