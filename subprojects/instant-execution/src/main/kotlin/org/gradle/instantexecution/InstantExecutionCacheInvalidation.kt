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

package org.gradle.instantexecution

import org.gradle.instantexecution.extensions.unsafeLazy
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.internal.hash.HashUtil
import org.gradle.util.GFileUtils
import org.gradle.util.GradleVersion
import java.io.File


class InstantExecutionCacheInvalidation(
    private val startParameter: InstantExecutionStartParameter
) {

    val cacheKey: String by unsafeLazy {
        // The following characters are not valid in task names
        // and can be used as separators: /, \, :, <, >, ", ?, *, |
        // except we also accept qualified task names with :, so colon is out.
        val cacheKey = StringBuilder()
        cacheKey.append(GradleVersion.current().version)
        val requestedTaskNames = startParameter.requestedTaskNames
        requestedTaskNames.joinTo(cacheKey, separator = "/", prefix = ">")
        val excludedTaskNames = startParameter.excludedTaskNames
        if (excludedTaskNames.isNotEmpty()) {
            excludedTaskNames.joinTo(cacheKey, prefix = "<", separator = "/")
        }
        val taskNames = startParameter.requestedTaskNames.asSequence() + excludedTaskNames.asSequence()
        val hasRelativeTaskName = taskNames.any { !it.startsWith(':') }
        if (hasRelativeTaskName) {
            // Because unqualified task names are resolved relative to the enclosing
            // sub-project according to `invocationDirectory`,
            // the relative invocation directory information must be part of the key.
            relativeChildPathOrNull(startParameter.currentDirectory, startParameter.rootDirectory)?.let { relativeSubDir ->
                cacheKey.append('*')
                cacheKey.append(relativeSubDir)
            }
        }
        HashUtil.createCompactMD5(cacheKey.toString())
    }

    /**
     * Returns the path of [target] relative to [base] if
     * [target] is a child of [base] or `null` otherwise.
     */
    private
    fun relativeChildPathOrNull(target: File, base: File): String? =
        GFileUtils.relativePathOf(target, base)
            .takeIf { !it.startsWith('.') }
}
