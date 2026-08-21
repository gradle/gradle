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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.internal.serialize.graph.codecs.WideningCodec


/**
 * Encodes and decodes [org.gradle.api.file.SourceDirectorySet] instances by
 * delegating entirely to [FileTreeCodec] (a `SourceDirectorySet` is a
 * [FileTreeInternal]). Adds a [WideningCodec] declaration so that a
 * `SourceDirectorySet` value flowing into a field or `Property<T>` whose
 * declared type cannot accept the codec's `FileTreeInternal` decode result is
 * rejected at configuration cache store time.
 *
 * Must be registered before [FileTreeCodec] in the bindings list — the
 * binding-walk picks the first match for a runtime type, and
 * `SourceDirectorySet` is a subtype of `FileTreeInternal`.
 */
class SourceDirectorySetCodec(
    fileCollectionFactory: FileCollectionFactory,
    directoryFileTreeFactory: DirectoryFileTreeFactory,
    fileOperations: FileOperations
) : FileTreeCodec(fileCollectionFactory, directoryFileTreeFactory, fileOperations),
    WideningCodec<FileTreeInternal> {

    override val decodedType: Class<FileTreeInternal> = FileTreeInternal::class.java

    override val publicDecodedType: Class<*> = FileTree::class.java

    override val wideningFix: String = "Use a ConfigurableFileCollection or ConfigurableFileTree instead."
}
