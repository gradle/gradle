/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFilePermissions
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.readEnum
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.Factory
import org.gradle.internal.serialize.graph.writeEnum
import org.gradle.internal.reflect.Instantiator


class DefaultCopySpecCodec(
    private val patternSetFactory: Factory<PatternSet>,
    private val fileCollectionFactory: FileCollectionFactory,
    private val objectFactory: ObjectFactory,
    private val instantiator: Instantiator,
    private val fileSystemOperations: FileSystemOperations
) : Codec<DefaultCopySpec> {

    override suspend fun WriteContext.encode(value: DefaultCopySpec) {
        encodePreservingIdentityOf(value) {
            writeNullableString(value.destinationDir?.path)
            write(value.sourceRootsForThisSpec)
            write(value.patterns)
            writeEnum(value.duplicatesStrategyForThisSpec)
            writeBoolean(value.includeEmptyDirs)
            writeBoolean(value.caseSensitive.get())
            writeString(value.filteringCharset)
            writeNullableSmallInt(value.dirPermissions.map(ConfigurableFilePermissions::toUnixNumeric).orNull)
            writeNullableSmallInt(value.filePermissions.map(ConfigurableFilePermissions::toUnixNumeric).orNull)
            writeCollection(value.copyActions)
            writeCollection(value.children)
        }
    }

    override suspend fun ReadContext.decode(): DefaultCopySpec {
        return decodePreservingIdentity { id ->
            val destPath = readNullableString()
            val sourceFiles = read() as FileCollection
            val patterns = read() as PatternSet
            val duplicatesStrategy = readEnum<DuplicatesStrategy>()
            val includeEmptyDirs = readBoolean()
            val isCaseSensitive = readBoolean()
            val filteringCharset = readString()
            val dirMode = readNullableSmallInt()
            val fileMode = readNullableSmallInt()
            val actions = readList().uncheckedCast<List<Action<FileCopyDetails>>>()
            val children = readList().uncheckedCast<List<CopySpecInternal>>()
            val copySpec = DefaultCopySpec(fileCollectionFactory, objectFactory, instantiator, patternSetFactory, destPath, sourceFiles, patterns, actions, children)
            copySpec.duplicatesStrategy = duplicatesStrategy
            copySpec.includeEmptyDirs = includeEmptyDirs
            copySpec.caseSensitive.set(isCaseSensitive)
            copySpec.filteringCharset = filteringCharset
            if (dirMode != null) {
                copySpec.dirPermissions.set(fileSystemOperations.permissions(dirMode))
            }
            if (fileMode != null) {
                copySpec.filePermissions.set(fileSystemOperations.permissions(fileMode))
            }
            isolate.identities.putInstance(id, copySpec)
            copySpec
        }
    }
}
