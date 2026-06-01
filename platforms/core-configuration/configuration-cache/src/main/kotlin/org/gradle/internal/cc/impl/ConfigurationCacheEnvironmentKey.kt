/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.encryption.EncryptionConfiguration
import org.gradle.internal.extensions.stdlib.unsafeLazy
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GFileUtils.relativePathOf
import java.io.File


/**
 * Sibling of [ConfigurationCacheKey] that hashes every component <strong>EXCEPT</strong>
 * the requested task names. Identifies the group of stored entries that share the same
 * environment (Gradle version, included builds, encryption, excluded tasks, etc.) and
 * thus could superset-share. [SupersetIndexLookup] filters among entries with the same
 * environment key but different requested-task lists.
 * <p>
 * [ConfigurationCacheKey] composes this type via [appendComponents] when assembling the
 * full hash. Add new env-level factors here; requested-task factors belong in
 * [ConfigurationCacheKey].
 */
@ServiceScope(Scope.BuildTree::class)
internal
class ConfigurationCacheEnvironmentKey(
    private val startParameter: ConfigurationCacheStartParameter,
    private val buildActionRequirements: BuildActionModelRequirements,
    private val encryptionConfiguration: EncryptionConfiguration
) {

    val string: String by unsafeLazy {
        Hashing.md5().newHasher().apply {
            putEnvironmentComponents()
        }.hash().toCompactString()
    }

    /** Append env-key components to [hasher]. Only [ConfigurationCacheKey] should call this; downstream consumers use [string]. */
    internal fun appendComponents(hasher: Hasher) = hasher.putEnvironmentComponents()

    override fun toString() = string

    override fun hashCode(): Int = string.hashCode()

    override fun equals(other: Any?): Boolean = (other as? ConfigurationCacheEnvironmentKey)?.string == string

    private
    fun Hasher.putEnvironmentComponents() {
        putString(GradleVersion.current().version)
        putAll(
            startParameter.includedBuilds.map {
                relativePathOf(it, startParameter.buildTreeRootDirectory)
            }
        )
        buildActionRequirements.appendKeyTo(this)

        // Excluded tasks belong to the environment: any -x difference forces exact-match scope.
        val excludedTaskNames = startParameter.excludedTaskNames
        putAll(excludedTaskNames)

        // Relative project/current dir matters when requested or excluded task names are relative.
        // This mirrors ConfigurationCacheKey.appendRequestedTasks() which hashes the relative dir
        // whenever any task name (requested or excluded) does not start with ':'.
        val requestedTaskNames = startParameter.requestedTaskNames
        val taskNames = requestedTaskNames.asSequence() + excludedTaskNames.asSequence()
        if (taskNames.any { !it.startsWith(':') }) {
            val projectDir = startParameter.projectDirectory
            if (projectDir != null) {
                putString(relativePathOf(projectDir, startParameter.buildTreeRootDirectory))
            } else {
                relativeChildPathOrNull(
                    startParameter.currentDirectory,
                    startParameter.buildTreeRootDirectory
                )?.let(::putString)
            }
        }

        putBoolean(startParameter.isOffline)
        putBoolean(startParameter.isIsolatedProjects)
        putBuildScan()
        putDevelocityUrl()
        putDevelocityPluginVersion()
        putBoolean(encryptionConfiguration.isEncrypting)
        putHash(encryptionConfiguration.encryptionKeyHashCode)
        putBoolean(startParameter.isDeduplicatingStrings)
        putBoolean(startParameter.isFineGrainedPropertyTracking)
        putBoolean(startParameter.isIntegrityCheckEnabled)
    }

    private
    fun Hasher.putBuildScan() {
        putByte(
            startParameter.run {
                when {
                    isNoBuildScan -> 0
                    isBuildScan -> 1
                    else -> 3
                }
            }
        )
    }

    private
    fun Hasher.putDevelocityUrl() {
        val develocityUrl = startParameter.develocityUrl
        if (develocityUrl != null) {
            putString(develocityUrl)
        }
    }

    private
    fun Hasher.putDevelocityPluginVersion() {
        val develocityPluginVersion = startParameter.develocityPluginVersion
        if (develocityPluginVersion != null) {
            putString(develocityPluginVersion)
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
        relativePathOf(target, base).takeIf { !it.startsWith('.') }
}
