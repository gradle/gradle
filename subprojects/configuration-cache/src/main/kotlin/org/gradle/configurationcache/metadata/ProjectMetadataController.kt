/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.metadata

import org.gradle.cache.internal.streams.ValueStore
import org.gradle.configurationcache.ConfigurationCacheStateStore
import org.gradle.configurationcache.StateType
import org.gradle.internal.component.local.model.LocalComponentMetadata
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.util.Path
import java.io.Closeable


internal
class ProjectMetadataController(
    private val store: ConfigurationCacheStateStore
) : Closeable {
    private
    val metadataStore by lazy {
        val writer = ValueStore.Writer<LocalComponentMetadata> { encoder, value ->
            encoder.writeString(value.toString())
        }
        val reader = ValueStore.Reader<LocalComponentMetadata> {
            TODO()
        }
        store.createValueStore(StateType.ProjectMetadata, writer, reader)
    }

    fun loadOrCreateProjectMetadata(identityPath: Path, creator: () -> LocalComponentMetadata): LocalComponentMetadata {
        println("-> load or create metadata for $identityPath")
        val metadata = creator()
        metadataStore.write(metadata)
        return metadata
    }

    override fun close() {
        CompositeStoppable.stoppable(metadataStore).stop()
    }
}
