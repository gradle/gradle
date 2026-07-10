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


@ServiceScope(Scope.BuildTree::class)
internal
class ConfigurationCacheKey(
    private val startParameter: ConfigurationCacheStartParameter,
    private val buildActionRequirements: BuildActionModelRequirements,
    private val encryptionConfiguration: EncryptionConfiguration
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

        putAll(
            startParameter.includedBuilds.map {
                relativePathOf(it, startParameter.buildTreeRootDirectory)
            }
        )

        buildActionRequirements.appendKeyTo(this)

        val hasRelativeTaskNames = sequenceOf(startParameter.requestedTaskNames, startParameter.excludedTaskNames).flatten()
            .any { !it.startsWith(':') }
        if (buildActionRequirements.isCreatesModel || (buildActionRequirements.isRunsTasks && hasRelativeTaskNames)) {
            // (1)
            // Tooling model queries target a specific project directory
            // (via `ProjectConnection.forProjectDirectory(...)`), and different
            // subprojects of the same root build can produce different models.
            // Include the project directory in the key so that consecutive model
            // queries against different subprojects do not collide.
            // (2)
            // Because unqualified task names are resolved relative to the selected
            // sub-project according to either `projectDirectory` or `currentDirectory`,
            // the relative directory information must be part of the key.
            appendTargetProjectDirectory()
        }

        // TODO:bamboo review with Adam
//        require(buildActionRequirements.isRunsTasks || startParameter.requestedTaskNames.isEmpty())
        if (buildActionRequirements.isRunsTasks) {
            putAll(startParameter.requestedTaskNames)
            putAll(startParameter.excludedTaskNames)
        }

        putBoolean(startParameter.isOffline)
        putBoolean(startParameter.isIsolatedProjects)
        // Keep entries from runs that ignore IP problems separate from normal entries, so removing
        // the flag is a clean cache miss and a normal build never reuses a possibly-incorrect entry.
        putBoolean(startParameter.isIsolatedProjectsDangerouslyIgnoreProblems)
        putBuildScan()
        putStringIfNotNull(startParameter.develocityUrl)
        putStringIfNotNull(startParameter.develocityPluginVersion)
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
    fun Hasher.appendTargetProjectDirectory() {
        val buildTreeRoot = startParameter.buildTreeRootDirectory
        val projectDir = startParameter.projectDirectory
        val relativeDir = if (projectDir != null) {
            relativePathOf(projectDir, buildTreeRoot)
        } else {
            relativeChildPathOrNull(startParameter.currentDirectory, buildTreeRoot)
        }
        putStringIfNotNull(relativeDir)
    }

    private
    fun Hasher.putAll(list: Collection<String>) {
        putInt(list.size)
        list.forEach(::putString)
    }

    private
    fun Hasher.putStringIfNotNull(value: String?) {
        if (value != null) {
            putString(value)
        }
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
