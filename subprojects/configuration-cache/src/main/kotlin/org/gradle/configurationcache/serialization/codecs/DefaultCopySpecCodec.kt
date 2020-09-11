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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.Action
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.decodePreservingIdentity
import org.gradle.configurationcache.serialization.encodePreservingIdentityOf
import org.gradle.configurationcache.serialization.readEnum
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.Factory
import org.gradle.configurationcache.serialization.writeEnum
import org.gradle.internal.reflect.Instantiator


internal
class DefaultCopySpecCodec(
    private val patternSetFactory: Factory<PatternSet>,
    private val fileCollectionFactory: FileCollectionFactory,
    private val instantiator: Instantiator
) : Codec<DefaultCopySpec> {

    override suspend fun WriteContext.encode(value: DefaultCopySpec) {
        encodePreservingIdentityOf(value) {
            write(value.destPath)
            write(value.sourceRootsForThisSpec)
            write(value.patterns)
            writeEnum(value.duplicatesStrategyForThisSpec)
            writeBoolean(value.includeEmptyDirs)
            writeBoolean(value.isCaseSensitive)
            writeString(value.filteringCharset)
            writeNullableSmallInt(value.dirMode)
            writeNullableSmallInt(value.fileMode)
            writeCollection(value.copyActions)
            writeCollection(value.children)
        }
    }

    override suspend fun ReadContext.decode(): DefaultCopySpec {
        return decodePreservingIdentity { id ->
            val destPath = read() as String?
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
            val copySpec = DefaultCopySpec(fileCollectionFactory, instantiator, patternSetFactory, destPath, sourceFiles, patterns, actions, children)
            copySpec.duplicatesStrategy = duplicatesStrategy
            copySpec.includeEmptyDirs = includeEmptyDirs
            copySpec.isCaseSensitive = isCaseSensitive
            copySpec.filteringCharset = filteringCharset
            if (dirMode != null) {
                copySpec.dirMode = dirMode
            }
            if (fileMode != null) {
                copySpec.fileMode = fileMode
            }
            isolate.identities.putInstance(id, copySpec)
            copySpec
        }
    }
}
