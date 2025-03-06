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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.internal.file.BaseDirFileResolver
import org.gradle.api.internal.file.DirectoryProviderPathToFileResolver
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodeBean
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.writeFile


class PathToFileResolverCodec(
    private val fileLookup: FileLookup,
) : Codec<PathToFileResolver> {

    override suspend fun WriteContext.encode(value: PathToFileResolver) {
        when (value) {
            is IdentityFileResolver -> {
                writeByte(1)
            }

            is BaseDirFileResolver -> {
                writeByte(2)
                encodePreservingSharedIdentityOf(value) {
                    writeFile(value.baseDir)
                }
            }

            is DirectoryProviderPathToFileResolver -> {
                writeByte(3)
                encodeBean(value)
            }

            else -> error("Unexpected type of ${PathToFileResolver::class.simpleName} for ${value.javaClass}")
        }
    }

    override suspend fun ReadContext.decode(): PathToFileResolver {
        return when (readByte()) {
            1.toByte() -> {
                fileLookup.fileResolver
            }

            2.toByte() -> {
                decodePreservingSharedIdentity {
                    fileLookup.getFileResolver(readFile())
                }
            }

            3.toByte() -> {
                decodeBean() as DirectoryProviderPathToFileResolver
            }

            else -> error("Unexpected encoding of ${PathToFileResolver::class.simpleName} type")
        }
    }
}
