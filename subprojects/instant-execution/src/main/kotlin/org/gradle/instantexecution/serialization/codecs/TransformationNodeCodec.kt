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

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.internal.artifacts.PreResolvedResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.transform.DefaultTransformer
import org.gradle.api.internal.artifacts.transform.LegacyTransformer
import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.WorkNodeAction
import org.gradle.execution.plan.ActionNode
import org.gradle.execution.plan.Node
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.service.ServiceRegistry
import java.io.File


internal
object TransformationNodeCodec : Codec<Node> {
    private
    val componentIdSerializer = ComponentIdentifierSerializer()

    override suspend fun WriteContext.encode(value: Node) {
        val id = sharedIdentities.getId(value)
        if (id != null) {
            writeSmallInt(id)
        } else {
            writeSmallInt(sharedIdentities.putInstance(value))

            // TODO - reuse the codec infrastructure to write nodes
            when (value) {
                is TransformationNode.InitialTransformationNode -> {
                    writeByte(1)

                    val artifact = value.artifact
                    // TODO - handle other artifact implementations
                    if (artifact !is DefaultResolvedArtifact) {
                        throw UnsupportedOperationException("Don't know how to serialize a resolved artifact of type ${artifact.javaClass.name}")
                    }

                    // Write the source artifact
                    writeString(artifact.file.absolutePath)
                    writeString(artifact.artifactName.name)
                    writeString(artifact.artifactName.type)
                    writeNullableString(artifact.artifactName.extension)
                    writeNullableString(artifact.artifactName.classifier)
                    // TODO - preserve the artifact id implementation instead of unpacking the component id
                    componentIdSerializer.write(this, artifact.id.componentIdentifier)
                    // TODO - preserve the artifact's owner id (or get rid of it as it's not used for transforms)

                    // Write the transformer
                    writeTransformer(value)

                    // Ignore the remaining state
                }
                is TransformationNode.ChainedTransformationNode -> {
                    writeByte(2)

                    // Write the previous node
                    encode(value.previousTransformationNode)

                    // Write the transformer
                    writeTransformer(value)

                    // Ignore the remaining state
                }
                // TODO - handle other node implementations
                else -> throw UnsupportedOperationException("Don't know how to serialize a node of type ${value.javaClass.name}")
            }
        }
    }

    override suspend fun ReadContext.decode(): Node {
        val id = readSmallInt()
        val instance = sharedIdentities.getInstance(id)
        if (instance != null) {
            return instance as Node
        }
        val node = when (readByte()) {
            1.toByte() -> {
                // Read the source artifact
                val file = File(readString())
                val artifactName = DefaultIvyArtifactName(readString(), readString(), readNullableString(), readNullableString())
                val componentId = componentIdSerializer.read(this)
                val artifact = PreResolvedResolvableArtifact(null, artifactName, ComponentFileArtifactIdentifier(componentId, file.name), file, TaskDependencyContainer.EMPTY)

                // Read the transformer class
                val implementationClass = readClass()

                // Create a dummy node that does not do anything
                ActionNode(object : WorkNodeAction {
                    override fun getProject(): Project? {
                        return null
                    }

                    override fun toString(): String {
                        return "$artifact transformed using ${implementationClass.name}"
                    }

                    override fun run(registry: ServiceRegistry) {
                        println("-> would transform $artifact using ${implementationClass.name}")
                    }
                })
            }
            2.toByte() -> {
                // Read the node that represents the previous transformation step
                val previous = decode()

                // Read the transformer class
                val implementationClass = readClass()

                // Create a dummy node that does not do anything
                ActionNode(object : WorkNodeAction {
                    override fun getProject(): Project? {
                        return null
                    }

                    override fun run(registry: ServiceRegistry) {
                        println("-> would transform output of $previous using ${implementationClass.name}")
                    }
                })
            }
            else -> throw UnsupportedOperationException()
        }

        sharedIdentities.putInstance(id, node)
        return node
    }

    private
    fun WriteContext.writeTransformer(value: TransformationNode) {
        val transformer = value.transformationStep.transformer
        if (transformer is LegacyTransformer) {
            writeClass(transformer.implementationClass)
        } else if (transformer is DefaultTransformer) {
            writeClass(transformer.implementationClass)
        } else {
            throw UnsupportedOperationException("Don't know how to serialize a transformer of type ${transformer.javaClass.name}")
        }
    }
}
