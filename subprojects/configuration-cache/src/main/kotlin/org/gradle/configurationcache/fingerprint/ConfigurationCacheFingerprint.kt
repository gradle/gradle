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

import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import java.io.File
import java.net.URI


internal
sealed class ConfigurationCacheFingerprint {

    data class GradleEnvironment(
        val gradleUserHomeDir: File,
        val jvm: String,
        val startParameterProperties: Map<String, Any?>,
        /**
         * Whether the instrumentation agent was used when computing the cache.
         * With the agent, the class paths may be stored differently, making the caches incompatible with one another.
         */
        val instrumentationAgentUsed: Boolean,
        /**
         * The file system paths that will be ignored during file system checks tracking for the cache fingerprint.
         * @see org.gradle.configurationcache.DefaultIgnoredConfigurationInputs
         */
        val ignoredFileSystemCheckInputPaths: String?
    ) : ConfigurationCacheFingerprint()

    data class InitScripts(
        val fingerprints: List<InputFile>
    ) : ConfigurationCacheFingerprint()

    data class WorkInputs(
        val workDisplayName: String,
        val fileSystemInputs: FileCollectionInternal,
        val fileSystemInputsFingerprint: HashCode
    ) : ConfigurationCacheFingerprint()

    data class InputFile(
        val file: File,
        val hash: HashCode
    ) : ConfigurationCacheFingerprint()

    data class DirectoryChildren(
        val file: File,
        val hash: HashCode
    ) : ConfigurationCacheFingerprint()

    data class InputFileSystemEntry(
        val file: File,
        val fileType: FileType
    ) : ConfigurationCacheFingerprint()

    data class ValueSource(
        val obtainedValue: ObtainedValue
    ) : ConfigurationCacheFingerprint()

    data class UndeclaredSystemProperty(
        val key: String,
        val value: Any?
    ) : ConfigurationCacheFingerprint()

    data class UndeclaredEnvironmentVariable(
        val key: String,
        val value: Any?
    ) : ConfigurationCacheFingerprint()

    data class RemoteScript(
        val uri: URI
    ) : ConfigurationCacheFingerprint()

    abstract class ChangingDependencyResolutionValue(
        val expireAt: Long
    ) : ConfigurationCacheFingerprint() {
        abstract val reason: String
    }

    class DynamicDependencyVersion(
        val displayName: String,
        expireAt: Long
    ) : ChangingDependencyResolutionValue(expireAt) {
        override val reason: String
            get() = "cached version information for $displayName has expired"
    }

    class ChangingModule(
        val displayName: String,
        expireAt: Long
    ) : ChangingDependencyResolutionValue(expireAt) {
        override val reason: String
            get() = "cached artifact information for $displayName has expired"
    }

    data class SystemPropertiesPrefixedBy(
        val prefix: String,
        val snapshot: Map<String, Any?>
    ) : ConfigurationCacheFingerprint() {
        companion object {
            /**
             * The placeholder for system properties modified by the build logic at the time of
             * reading. Such properties shouldn't be taken into account when comparing snapshots.
             */
            val IGNORED: Any = Ignored.INSTANCE

            // Enum ensures that only one instance of INSTANCE exists and even deserialization
            // doesn't create a new one. The `object` has no such guarantee.
            private
            enum class Ignored {
                INSTANCE
            }
        }
    }

    data class EnvironmentVariablesPrefixedBy(
        val prefix: String,
        val snapshot: Map<String, String?>
    ) : ConfigurationCacheFingerprint()
}


internal
typealias ObtainedValue = ValueSourceProviderFactory.ValueListener.ObtainedValue<Any, ValueSourceParameters>
