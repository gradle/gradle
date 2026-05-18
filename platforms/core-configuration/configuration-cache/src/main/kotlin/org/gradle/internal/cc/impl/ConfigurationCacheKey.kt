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
 * Identifies a specific configuration cache entry on disk.
 * <p>
 * The resulting [string] is an MD5 hex digest that doubles as the entry's
 * directory name under the configuration cache root (one entry per unique key).
 * Equality and `hashCode` are defined by that digest, so two instances built
 * independently from identical start parameters compare equal — this is the
 * contract that lets CC locate its prior entry on a re-invocation.
 * <p>
 * <strong>Hashed inputs:</strong>
 * <ul>
 *   <li>Gradle version</li>
 *   <li>Included builds (paths relative to the build tree root)</li>
 *   <li>[BuildActionModelRequirements] — the build-action shape (run-tasks vs. tooling-model fetch, etc.)</li>
 *   <li><strong>Requested task names</strong> and excluded task names — only when
 *       [BuildActionModelRequirements.isRunsTasks] is true; when any task name is
 *       relative, also the project/current directory relative to the build tree root</li>
 *   <li>Offline, isolated-projects, build-scan, Develocity URL/plugin-version, encryption
 *       on/off + key hash, string deduplication, fine-grained property tracking, integrity-check</li>
 * </ul>
 * <p>
 * <strong>Relation to [ConfigurationCacheEnvironmentKey]:</strong>
 * [ConfigurationCacheEnvironmentKey] hashes the same components <em>except</em>
 * the requested task names. It identifies the group of entries that share a
 * configuration-cache "environment"; this class identifies one specific
 * member of that group. Two builds whose environment keys agree but whose
 * `ConfigurationCacheKey`s differ are candidates for superset matching (see
 * `SupersetIndex`). Excluded task names live in the environment key because
 * any `-x` difference forces exact-match scope — v1 superset lookup does not
 * reason about exclusions.
 * <p>
 * <strong>Duplication caveat:</strong> the input list here is intentionally
 * duplicated by [ConfigurationCacheEnvironmentKey] for v1. Any new component
 * added here must also be added there (or be intentionally part of the
 * requested-tasks-only delta), or different environments will silently share
 * a superset index.
 */
@ServiceScope(Scope.BuildTree::class)
internal
class ConfigurationCacheKey(
    private val startParameter: ConfigurationCacheStartParameter,
    private val buildActionRequirements: BuildActionModelRequirements,
    private val encryptionConfiguration: EncryptionConfiguration
) {

    /**
     * Stable MD5 digest of the hashed inputs. Used as the entry's directory
     * name and as the value backing [equals] / [hashCode].
     */
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

        putAll(
            startParameter.includedBuilds.map {
                relativePathOf(it, startParameter.buildTreeRootDirectory)
            }
        )

        buildActionRequirements.appendKeyTo(this)

        // TODO:bamboo review with Adam
//        require(buildActionRequirements.isRunsTasks || startParameter.requestedTaskNames.isEmpty())
        if (buildActionRequirements.isRunsTasks) {
            appendRequestedTasks()
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
        // Integrity check affects the way fingerprint is stored.
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

    /**
     * Hashes requested and excluded task names. When any task name is relative
     * (does not start with `:`), also hashes the project/current directory
     * relative to the build tree root, because relative names resolve against
     * that directory — so the same `gradle foo` from two different subdirs
     * must produce different keys.
     */
    private
    fun Hasher.appendRequestedTasks() {
        val requestedTaskNames = startParameter.requestedTaskNames
        putAll(requestedTaskNames)

        val excludedTaskNames = startParameter.excludedTaskNames
        putAll(excludedTaskNames)

        val taskNames = requestedTaskNames.asSequence() + excludedTaskNames.asSequence()
        val hasRelativeTaskName = taskNames.any { !it.startsWith(':') }
        if (hasRelativeTaskName) {
            val projectDir = startParameter.projectDirectory
            if (projectDir != null) {
                relativePathOf(
                    projectDir,
                    startParameter.buildTreeRootDirectory
                ).let { relativeProjectDir ->
                    putString(relativeProjectDir)
                }
            } else {
                relativeChildPathOrNull(
                    startParameter.currentDirectory,
                    startParameter.buildTreeRootDirectory
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
