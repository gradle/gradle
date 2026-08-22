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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.properties.GradlePropertyScope
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.graph.codecs.ValueObject
import java.io.File
import java.net.URI


/**
 * A fingerprint value whose meaning depends on the state of the system properties at the moment it was
 * recorded, because the build logic can change them while the build is being configured.
 *
 * Fingerprint values are checked in an order that has nothing to do with the order they were recorded in,
 * so an entry cannot rely on the changes recorded before it having been replayed already. Instead, every
 * such entry carries the version of the system properties it observed, and the checker resolves it against
 * that version. See [VersionedSystemProperties].
 */
internal
sealed interface SystemPropertiesVersioned {

    val systemPropertiesVersion: Long
}


/**
 * A value that observed the system properties at [systemPropertiesVersion] and is checked against them.
 */
internal
interface ReadsSystemProperties : SystemPropertiesVersioned


/**
 * A change to the system properties, which produced [systemPropertiesVersion].
 */
internal
interface ChangesSystemProperties : SystemPropertiesVersioned


internal
sealed class ConfigurationCacheFingerprint : ValueObject {

    data class GradleEnvironment(
        val gradleUserHomeDir: File,
        val jvm: String,
        /**
         * Whether to exclude from input tracking the undeclared inputs accessed
         * while resolving and storing work graph or while building the model result of the build action.
         *
         * This is a temporary opt-out flag after a change was made in that behavior.
         */
        val ignoreInputsDuringConfigurationCacheStore: Boolean,
        /**
         * Whether the instrumentation agent was used when computing the cache.
         * With the agent, the class paths may be stored differently, making the caches incompatible with one another.
         */
        val instrumentationAgentUsed: Boolean,

        /**
         * The file system paths that will be ignored during file system checks tracking for the cache fingerprint.
         * @see org.gradle.internal.cc.impl.DefaultIgnoredConfigurationInputs
         */
        val ignoredFileSystemCheckInputPaths: String?
    ) : ConfigurationCacheFingerprint()

    data class InitScripts(
        val fingerprints: List<InputFile>
    ) : ConfigurationCacheFingerprint()

    data class StartParameterProjectProperties(
        val snapshot: Map<String, Any?>
    ) : ConfigurationCacheFingerprint()

    data class MissingBuildSrcDir(
        val buildSrcDir: File,
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
        val obtainedValue: ObtainedValue,
        override val systemPropertiesVersion: Long
    ) : ConfigurationCacheFingerprint(), ReadsSystemProperties

    data class SystemPropertyChanged(
        val key: Any,
        val value: Any?,
        override val systemPropertiesVersion: Long
    ) : ConfigurationCacheFingerprint(), ChangesSystemProperties

    data class SystemPropertyRemoved(
        val key: Any,
        override val systemPropertiesVersion: Long
    ) : ConfigurationCacheFingerprint(), ChangesSystemProperties

    data class SystemPropertiesCleared(
        override val systemPropertiesVersion: Long
    ) : ConfigurationCacheFingerprint(), ChangesSystemProperties

    data class UndeclaredSystemProperty(
        val key: String,
        val value: Any?,
        override val systemPropertiesVersion: Long
    ) : ConfigurationCacheFingerprint(), ReadsSystemProperties

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
        val snapshot: Map<String, Any?>,
        override val systemPropertiesVersion: Long
    ) : ConfigurationCacheFingerprint(), ReadsSystemProperties

    data class EnvironmentVariablesPrefixedBy(
        val prefix: String,
        val snapshot: Map<String, String?>
    ) : ConfigurationCacheFingerprint()

    data class GradlePropertiesLoaded(
        val propertyScope: GradlePropertyScope,
        val propertiesDir: File,
        override val systemPropertiesVersion: Long
    ) : ConfigurationCacheFingerprint(), ChangesSystemProperties

    data class GradleProperty(
        val propertyScope: GradlePropertyScope,
        val propertyName: String,
        val propertyValue: Any?
    ) : ConfigurationCacheFingerprint()

    data class GradlePropertiesPrefixedBy(
        val propertyScope: GradlePropertyScope,
        val prefix: String,
        val snapshot: Map<String, String?>
    ) : ConfigurationCacheFingerprint()
}


internal
typealias ObtainedValue = ValueSourceProviderFactory.ValueListener.ObtainedValue<Any, ValueSourceParameters>
