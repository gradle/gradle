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

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readCollectionInto
import org.gradle.instantexecution.serialization.writeCollection


internal
object ArtifactCollectionCodec : Codec<ArtifactCollection> {

    override suspend fun WriteContext.encode(value: ArtifactCollection) {
        write(value.artifactFiles)
        writeCollection(value.artifacts)
        writeCollection(value.failures)
    }

    override suspend fun ReadContext.decode(): ArtifactCollection {
        val files = read() as FileCollection
        val artifacts = readCollectionInto { LinkedHashSet<Any?>(it) }
        val failures = readCollectionInto { ArrayList<Any?>(it) }
        return FixedArtifactCollection(
            files,
            artifacts.uncheckedCast(),
            failures.uncheckedCast()
        )
    }
}


class FixedArtifactCollection(
    private val fileCollection: FileCollection,
    private val artifacts: MutableSet<ResolvedArtifactResult>,
    private val failures: List<Throwable>
) : ArtifactCollection {

    override fun getFailures() = failures

    override fun iterator(): MutableIterator<ResolvedArtifactResult> =
        artifacts.iterator()

    override fun getArtifactFiles(): FileCollection =
        fileCollection

    override fun getArtifacts(): Set<ResolvedArtifactResult> =
        artifacts
}
