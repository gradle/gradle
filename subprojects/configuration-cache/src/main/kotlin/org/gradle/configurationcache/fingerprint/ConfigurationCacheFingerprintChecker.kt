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

package org.gradle.configurationcache.fingerprint

import org.gradle.api.Describable
import org.gradle.api.internal.GeneratedSubclasses.unpackType
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.internal.hash.HashCode
import org.gradle.internal.util.NumberUtil.ordinal
import java.io.File


internal
typealias InvalidationReason = String


internal
class ConfigurationCacheFingerprintChecker(private val host: Host) {

    interface Host {
        val gradleUserHomeDir: File
        val allInitScripts: List<File>
        val buildStartTime: Long
        fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode
        fun hashCodeOf(file: File): HashCode?
        fun displayNameOf(fileOrDirectory: File): String
        fun instantiateValueSourceOf(obtainedValue: ObtainedValue): ValueSource<Any, ValueSourceParameters>
    }

    suspend fun ReadContext.checkFingerprint(): InvalidationReason? {
        // TODO: log some debug info
        while (true) {
            when (val input = read()) {
                null -> return null
                is ConfigurationCacheFingerprint.TaskInputs -> input.run {
                    val currentFingerprint = host.fingerprintOf(fileSystemInputs)
                    if (currentFingerprint != fileSystemInputsFingerprint) {
                        // TODO: summarize what has changed (see https://github.com/gradle/configuration-cache/issues/282)
                        return "an input to task '$taskPath' has changed"
                    }
                }
                is ConfigurationCacheFingerprint.InputFile -> input.run {
                    if (hasFileChanged(file, hash)) {
                        return "file '${displayNameOf(file)}' has changed"
                    }
                }
                is ConfigurationCacheFingerprint.ValueSource -> input.run {
                    val reason = checkFingerprintValueIsUpToDate(obtainedValue)
                    if (reason != null) return reason
                }
                is ConfigurationCacheFingerprint.InitScripts -> input.run {
                    val reason = checkInitScriptsAreUpToDate(fingerprints, host.allInitScripts)
                    if (reason != null) return reason
                }
                is ConfigurationCacheFingerprint.UndeclaredSystemProperty -> input.run {
                    if (isDefined(key)) {
                        return "system property '$key' has changed"
                    }
                }
                is ConfigurationCacheFingerprint.ChangingDependencyResolutionValue -> input.run {
                    if (host.buildStartTime >= expireAt) {
                        return reason
                    }
                }
                is ConfigurationCacheFingerprint.GradleEnvironment -> input.run {
                    if (host.gradleUserHomeDir != gradleUserHomeDir) {
                        return "Gradle user home directory has changed"
                    }
                    if (jvmFingerprint() != jvm) {
                        return "JVM has changed"
                    }
                }
                else -> throw IllegalStateException("Unexpected configuration cache fingerprint: $input")
            }
        }
    }

    private
    fun checkInitScriptsAreUpToDate(
        previous: List<ConfigurationCacheFingerprint.InputFile>,
        current: List<File>
    ): InvalidationReason? =
        when (val upToDatePrefix = countUpToDatePrefixOf(previous, current)) {
            previous.size -> {
                val added = current.size - upToDatePrefix
                when {
                    added == 1 -> "init script '${displayNameOf(current[upToDatePrefix])}' has been added"
                    added > 1 -> "init script '${displayNameOf(current[upToDatePrefix])}' and ${added - 1} more have been added"
                    else -> null
                }
            }
            current.size -> {
                val removed = previous.size - upToDatePrefix
                when {
                    removed == 1 -> "init script '${displayNameOf(previous[upToDatePrefix].file)}' has been removed"
                    removed > 1 -> "init script '${displayNameOf(previous[upToDatePrefix].file)}' and ${removed - 1} more have been removed"
                    else -> null
                }
            }
            else -> {
                when (val modifiedScript = current[upToDatePrefix]) {
                    previous[upToDatePrefix].file -> "init script '${displayNameOf(modifiedScript)}' has changed"
                    else -> "content of ${ordinal(upToDatePrefix + 1)} init script, '${displayNameOf(modifiedScript)}', has changed"
                }
            }
        }

    private
    fun countUpToDatePrefixOf(
        previous: List<ConfigurationCacheFingerprint.InputFile>,
        current: List<File>
    ): Int = current.zip(previous)
        .takeWhile { (initScript, fingerprint) -> isUpToDate(initScript, fingerprint.hash) }
        .count()

    private
    fun checkFingerprintValueIsUpToDate(obtainedValue: ObtainedValue): InvalidationReason? {
        val valueSource = host.instantiateValueSourceOf(obtainedValue)
        if (obtainedValue.value.get() != valueSource.obtain()) {
            return buildLogicInputHasChanged(valueSource)
        }
        return null
    }

    private
    fun isDefined(key: String): Boolean =
        System.getProperty(key) != null

    private
    fun hasFileChanged(file: File, originalHash: HashCode?) =
        !isUpToDate(file, originalHash)

    private
    fun isUpToDate(file: File, originalHash: HashCode?) =
        host.hashCodeOf(file) == originalHash

    private
    fun displayNameOf(file: File) =
        host.displayNameOf(file)

    private
    fun buildLogicInputHasChanged(valueSource: ValueSource<Any, ValueSourceParameters>): InvalidationReason =
        (valueSource as? Describable)?.let {
            it.displayName + " has changed"
        } ?: "a build logic input of type '${unpackType(valueSource).simpleName}' has changed"
}
