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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.readNonNull


class ConfigurableFileCollectionCodec(
    private val codec: FileCollectionCodec,
    private val fileCollectionFactory: FileCollectionFactory
) : Codec<ConfigurableFileCollection> {
    override suspend fun WriteContext.encode(value: ConfigurableFileCollection) {
        require(value is DefaultConfigurableFileCollection)
        encodePreservingIdentityOf(value) {
            write(value.resolver)
            codec.run {
                encodeContents(value)
            }
            writeBoolean(value.isFinalizing)
        }
    }

    override suspend fun ReadContext.decode(): ConfigurableFileCollection {
        return decodePreservingIdentity { id ->
            val resolver = readNonNull<PathToFileResolver>()
            val contents = codec.run { decodeContents() }
            val fileCollection = fileCollectionFactory.withResolver(resolver).configurableFiles()
            fileCollection.from(contents)
            if (readBoolean()) {
                fileCollection.finalizeValue()
            }
            isolate.identities.putInstance(id, fileCollection)
            fileCollection
        }
    }
}
