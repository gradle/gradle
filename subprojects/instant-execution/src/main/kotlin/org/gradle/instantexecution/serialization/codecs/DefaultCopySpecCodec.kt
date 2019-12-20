/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readList
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.reflect.Instantiator


internal
class DefaultCopySpecCodec(
    private val fileResolver: FileResolver,
    private val fileCollectionFactory: FileCollectionFactory,
    private val instantiator: Instantiator
) : Codec<DefaultCopySpec> {

    override suspend fun WriteContext.encode(value: DefaultCopySpec) {
        write(value.destPath)
        write(value.resolveSourceFiles())
        write(value.patterns)
        writeCollection(value.children)
    }

    override suspend fun ReadContext.decode(): DefaultCopySpec {
        val destPath = read() as String?
        val sourceFiles = read() as FileCollection
        val patterns = read() as PatternSet
        val children = readList() as List<CopySpecInternal>
        return DefaultCopySpec(fileResolver, fileCollectionFactory, instantiator, destPath, sourceFiles, patterns, children)
    }
}
