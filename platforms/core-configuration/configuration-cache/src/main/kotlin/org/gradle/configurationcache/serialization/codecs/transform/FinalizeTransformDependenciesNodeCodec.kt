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

package org.gradle.configurationcache.serialization.codecs.transform

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readNonNull


class FinalizeTransformDependenciesNodeCodec : Codec<DefaultTransformUpstreamDependenciesResolver.FinalizeTransformDependencies> {
    override suspend fun WriteContext.encode(value: DefaultTransformUpstreamDependenciesResolver.FinalizeTransformDependencies) {
        write(value.selectedArtifacts())
    }

    override suspend fun ReadContext.decode(): DefaultTransformUpstreamDependenciesResolver.FinalizeTransformDependencies {
        val artifacts = readNonNull<FileCollectionInternal>()
        return object : DefaultTransformUpstreamDependenciesResolver.FinalizeTransformDependencies() {
            override fun visitDependencies(context: TaskDependencyResolveContext) {
                throw UnsupportedOperationException("Should not be called")
            }

            override fun selectedArtifacts(): FileCollection {
                return artifacts
            }
        }
    }
}
