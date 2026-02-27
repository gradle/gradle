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

package org.gradle.internal.serialize.codecs.dm

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultFileCollectionDependency
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.internal.serialize.codecs.core.FileCollectionCodec
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readNonNull

/**
 * Codec for [DefaultFileCollectionDependency].
 * <p>
 * Delegates to [FileCollectionCodec] for the serialization of the file collection.
 */
class FileCollectionDependencyCodec(
    private val fileCollectionCodec: FileCollectionCodec
) : Codec<DefaultFileCollectionDependency> {
    override suspend fun WriteContext.encode(value: DefaultFileCollectionDependency) {
        writeNullableString(value.reason)
        if (value.targetComponentId == null) {
            writeByte(0)
        } else {
            writeByte(1)
            write(value.targetComponentId!!)
        }
        fileCollectionCodec.run {
            encodeContents(value.files as FileCollectionInternal)
        }
    }

    override suspend fun ReadContext.decode(): DefaultFileCollectionDependency {
        val reason: String? = readNullableString()
        val targetComponentIdPresent = readByte().toInt() == 1
        val targetComponentId = if (targetComponentIdPresent) {
            readNonNull<ComponentIdentifier>()
        } else {
            null
        }
        val files = fileCollectionCodec.run { decodeContents() }

        val result = if (targetComponentIdPresent) {
            DefaultFileCollectionDependency(targetComponentId!!, files)
        } else {
            DefaultFileCollectionDependency(files)
        }
        return result.apply { because(reason) }
    }
}
