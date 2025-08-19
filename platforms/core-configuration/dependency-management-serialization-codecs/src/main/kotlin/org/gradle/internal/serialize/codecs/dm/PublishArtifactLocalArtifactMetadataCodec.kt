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

package org.gradle.internal.serialize.codecs.dm

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.publish.ImmutablePublishArtifact
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyContainerInternal
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.TaskDependencyResolver
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeFile

/**
 * This class exists because PublishArtifactLocalArtifactMetadata is used as its own id, and so when serialized as an id causes
 * a lot of unnecessary and unserializable state to be dragged in.
 *
 * A better change would be to split an immutable id type out of PublishArtifactLocalArtifactMetadata (or reuse one of the existing
 * implementations). However, the Eclipse tooling model builder assumes that the id and metadata objects are the same and also that
 * the metadata object provides access to the backing PublishArtifact. This makes the better change too large to undertake at this point,
 * so for now just use some custom serialization that also makes assumptions and improve the Eclipse tooling model builder later.
 */
class PublishArtifactLocalArtifactMetadataCodec(
    private val taskDependencyResolver: TaskDependencyResolver
) : Codec<PublishArtifactLocalArtifactMetadata> {
    override suspend fun WriteContext.encode(value: PublishArtifactLocalArtifactMetadata) {
        write(value.componentIdentifier)
        value.publishArtifact.run {
            // TODO: We need to also serialize the TaskDependencyContainer of the publish artifact.
            // Otherwise, the artifacts that we load in the ProjectMetdataController are not able
            // to be used by dependency resolution to build task dependency graphs.
            writeString(name)
            writeString(extension)
            writeString(type)
            writeNullableString(classifier)
            writeFile(file)

            val nodes = taskDependencyResolver.resolveDependenciesFor(null, this)
            writeCollection(nodes) {
                encodePreservingIdentityOf(it) {
                    write(it)
                }
            }
        }
    }

    override suspend fun ReadContext.decode(): PublishArtifactLocalArtifactMetadata {
        val componentId = read() as ComponentIdentifier
        val name = readString()
        val extension = readString()
        val type = readString()
        val classifier = readNullableString()
        val file = readFile()

        val nodes = readList {
            decodePreservingIdentity {
                read() as Node
            }
        }

        val publishArtifact = read() as ImmutablePublishArtifact
        return PublishArtifactLocalArtifactMetadata(
            componentId,
            ImmutablePublishArtifact(
                name,
                extension,
                type,
                classifier,
                file,
                buildTaskDependency(nodes)
            )
        )
    }

    private fun buildTaskDependency(nodes: List<Node>): TaskDependencyContainerInternal {
        return TaskDependencyInternal.EMPTY
    }
}
