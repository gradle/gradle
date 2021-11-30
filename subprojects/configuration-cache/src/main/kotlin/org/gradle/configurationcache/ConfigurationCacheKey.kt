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

package org.gradle.configurationcache

import org.gradle.configurationcache.extensions.unsafeLazy
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GFileUtils.relativePathOf
import java.io.File


@ServiceScope(Scopes.BuildTree::class)
class ConfigurationCacheKey(
    private val startParameter: ConfigurationCacheStartParameter,
    private val buildActionRequirements: BuildActionModelRequirements
) {

    val string: String by unsafeLazy {
        Hashing.md5().newHasher().apply {
            putCacheKeyComponents()
        }.hash().toCompactString()
    }

    override fun toString() = string

    override fun hashCode(): Int = string.hashCode()

    override fun equals(other: Any?): Boolean = (other as? ConfigurationCacheKey)?.string == string

    private
    fun Hasher.putCacheKeyComponents() {
        putString(GradleVersion.current().version)

        putString(
            startParameter.settingsFile?.let {
                relativePathOf(it, startParameter.rootDirectory)
            } ?: ""
        )

        putAll(
            startParameter.includedBuilds.map {
                relativePathOf(it, startParameter.rootDirectory)
            }
        )

        buildActionRequirements.appendKeyTo(this)

        // TODO:bamboo review with Adam
//        require(buildActionRequirements.isRunsTasks || startParameter.requestedTaskNames.isEmpty())
        if (buildActionRequirements.isRunsTasks) {
            appendRequestedTasks()
        }
    }

    private
    fun Hasher.appendRequestedTasks() {
        val requestedTaskNames = startParameter.requestedTaskNames
        putAll(requestedTaskNames)

        val excludedTaskNames = startParameter.excludedTaskNames
        putAll(excludedTaskNames)

        val taskNames = requestedTaskNames.asSequence() + excludedTaskNames.asSequence()
        val hasRelativeTaskName = taskNames.any { !it.startsWith(':') }
        if (hasRelativeTaskName) {
            // Because unqualified task names are resolved relative to the selected
            // sub-project according to either `projectDirectory` or `currentDirectory`,
            // the relative directory information must be part of the key.
            val projectDir = startParameter.projectDirectory
            if (projectDir != null) {
                relativePathOf(
                    projectDir,
                    startParameter.rootDirectory
                ).let { relativeProjectDir ->
                    putString(relativeProjectDir)
                }
            } else {
                relativeChildPathOrNull(
                    startParameter.currentDirectory,
                    startParameter.rootDirectory
                )?.let { relativeSubDir ->
                    putString(relativeSubDir)
                }
            }
        }
    }

    private
    fun Hasher.putAll(list: Collection<String>) {
        putInt(list.size)
        list.forEach(::putString)
    }

    /**
     * Returns the path of [target] relative to [base] if
     * [target] is a child of [base] or `null` otherwise.
     */
    private
    fun relativeChildPathOrNull(target: File, base: File): String? =
        relativePathOf(target, base)
            .takeIf { !it.startsWith('.') }
}
