/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.util.internal.GFileUtils
import java.io.File


internal class ConfigurationCacheInputFileChecker(
    private val host: Host,
) {

    @ServiceScope(Scope.BuildTree::class)
    interface Host {
        fun hashCodeOf(file: File): HashCode
        fun hashCodeAndTypeOf(file: File): Pair<HashCode, FileType>
        fun displayNameOf(fileOrDirectory: File): String
    }

    enum class FileUpToDateStatus {
        Unchanged,
        ContentsChanged,
        TypeChanged,
        Removed
    }

    fun StructuredMessage.Builder.check(file: File, originalHash: HashCode): StructuredMessage.Builder? =
        when (checkFileUpToDateStatus(file, originalHash)) {
            FileUpToDateStatus.ContentsChanged -> text("file ").reference(host.displayNameOf(file)).text(" has changed")
            FileUpToDateStatus.Removed -> text("file ").reference(host.displayNameOf(file)).text(" has been removed")
            FileUpToDateStatus.TypeChanged -> text("file ").reference(host.displayNameOf(file)).text(" has been replaced by a directory")
            FileUpToDateStatus.Unchanged -> null
        }

    fun checkFileUpToDateStatus(file: File, originalHash: HashCode): FileUpToDateStatus {
        val (hash, type) = host.hashCodeAndTypeOf(file)
        if (hash == originalHash) {
            return FileUpToDateStatus.Unchanged
        }
        return when (type) {
            FileType.RegularFile -> FileUpToDateStatus.ContentsChanged
            FileType.Directory -> FileUpToDateStatus.TypeChanged
            FileType.Missing -> FileUpToDateStatus.Removed
        }
    }
}


internal class DefaultConfigurationCacheInputFileCheckerHost(
    private val fileSystemAccess: FileSystemAccess,
    private val startParameter: ConfigurationCacheStartParameter
) : ConfigurationCacheInputFileChecker.Host {

    override fun hashCodeOf(file: File): HashCode =
        locationSnapshot(file).hash

    override fun hashCodeAndTypeOf(file: File): Pair<HashCode, FileType> =
        locationSnapshot(file).run {
            hash to type
        }

    override fun displayNameOf(fileOrDirectory: File): String =
        GFileUtils.relativePathOf(fileOrDirectory, startParameter.rootDirectory)

    private fun locationSnapshot(file: File): FileSystemLocationSnapshot =
        fileSystemAccess.read(file.absolutePath)
}


/**
 * Builds a structured message with a given [block], but if null is returned from the block, discards the message.
 * @return built message or null if [block] returns null
 */
internal
inline fun structuredMessageOrNull(block: StructuredMessage.Builder.() -> StructuredMessage.Builder?): StructuredMessage? =
    StructuredMessage.Builder().run { block() }?.build()
