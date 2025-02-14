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

import org.gradle.api.internal.file.FileLookup
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.writeFile


class PathToFileResolverCodec(
    private val fileLookup: FileLookup,
) : Codec<PathToFileResolver> {

    override suspend fun WriteContext.encode(value: PathToFileResolver) {
        val baseDir = value.baseDir
        if (baseDir == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeFile(baseDir)
        }
    }

    override suspend fun ReadContext.decode(): PathToFileResolver? {
        val hasBaseDir = readBoolean()
        return if (!hasBaseDir) {
            fileLookup.fileResolver
        } else {
            fileLookup.getFileResolver(readFile())
        }
    }
}
