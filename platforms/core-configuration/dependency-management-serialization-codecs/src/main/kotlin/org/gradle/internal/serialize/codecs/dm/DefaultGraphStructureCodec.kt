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

import com.google.common.collect.ImmutableList
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DefaultGraphStructure
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.writeCollection
import java.util.BitSet

class DefaultGraphStructureCodec(
    private val immutableAttributesCodec: ImmutableAttributesCodec,
    private val immutableCapabilitiesCodec: ImmutableCapabilitiesCodec,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory
) : Codec<DefaultGraphStructure> {

    override suspend fun WriteContext.encode(value: DefaultGraphStructure) {
        writeNodes(value.nodes())
        writeEdges(value.edges())
        writeComponents(value.components())
    }

    private
    suspend fun WriteContext.writeNodes(nodes: DefaultGraphStructure.DefaultNodes) {
        writeSmallInt(nodes.root())
        writeIntList(nodes.owners())
        writeCollection(nodes.attributes()) { attributes ->
            immutableAttributesCodec.run {
                encode(attributes)
            }
        }
        writeCollection(nodes.capabilities()) { capabilities ->
            immutableCapabilitiesCodec.run {
                encode(capabilities)
            }
        }
        writeCollection(nodes.variantNames()) {
            writeString(it)
        }
        write(nodes.externalVariantIndices())
    }

    private
    suspend fun WriteContext.writeEdges(edges: DefaultGraphStructure.DefaultEdges) {
        writeIntList(edges.indices())
        writeCollection(edges.selectors()) {
            write(it)
        }
        write(edges.constraints())
        writeIntList(edges.targetNodeIndices())
        write(edges.failures())
    }

    private
    suspend fun WriteContext.writeComponents(components: DefaultGraphStructure.DefaultComponents) {
        writeCollection(components.selectionReasons()) {
            write(it)
        }
        writeCollection(components.repositoryNames()) {
            writeNullableString(it)
        }
        writeCollection(components.ids()) {
            write(it)
        }
        writeCollection(components.moduleVersionIds()) {
            writeString(it.group)
            writeString(it.name)
            writeString(it.version)
        }
    }

    private
    fun WriteContext.writeIntList(list: IntList) {
        writeSmallInt(list.size)
        val it = list.intIterator()
        while (it.hasNext()) {
            writeInt(it.nextInt())
        }
    }

    override suspend fun ReadContext.decode(): DefaultGraphStructure {
        val nodes: DefaultGraphStructure.DefaultNodes = readNodes()
        val edges: DefaultGraphStructure.DefaultEdges = readEdges()
        val components: DefaultGraphStructure.DefaultComponents = readComponents()
        return DefaultGraphStructure(nodes, edges, components)
    }

    private
    suspend fun ReadContext.readNodes() : DefaultGraphStructure.DefaultNodes {
        val root = readSmallInt()
        val owners = readIntList()
        val attributes = readImmutableList {
            immutableAttributesCodec.run {
                decode()
            }
        }
        val capabilities = readImmutableList {
            immutableCapabilitiesCodec.run {
                decode()
            }
        }
        val variantNames = readImmutableList {
            readString()
        }
        val externalVariantIndices = read() as Int2IntMap
        return DefaultGraphStructure.DefaultNodes(root, owners, attributes, capabilities, variantNames, externalVariantIndices)
    }

    private
    suspend fun ReadContext.readEdges() : DefaultGraphStructure.DefaultEdges {
        val indices = readIntList()
        val selectors = readImmutableList {
            read() as ComponentSelector
        }
        val constraints = read() as BitSet
        val targetNodeIndices = readIntList()
        @Suppress("UNCHECKED_CAST") val failures = read() as Int2ObjectMap<GraphStructure.Edges.EdgeFailure>
        return DefaultGraphStructure.DefaultEdges(indices, selectors, constraints, targetNodeIndices, failures)
    }

    private
    suspend fun ReadContext.readComponents() : DefaultGraphStructure.DefaultComponents {
        val selectionReasons = readImmutableList {
            read() as ComponentSelectionReasonInternal
        }
        val repositoryNames = readList {
            readNullableString()
        }
        val ids = readImmutableList {
            read() as ComponentIdentifier
        }
        val moduleVersionIds = readImmutableList {
            val group = readString()
            val name = readString()
            val version = readString()
            moduleIdentifierFactory.moduleWithVersion(group, name, version)
        }
        return DefaultGraphStructure.DefaultComponents(selectionReasons, repositoryNames, ids, moduleVersionIds)
    }

    private
    fun ReadContext.readIntList() : IntList {
        val size = readSmallInt()
        val list = IntArrayList(size)
        repeat(size) {
            list.add(readInt())
        }
        return list
    }

    private
    inline fun <T: Any> ReadContext.readImmutableList(reader: () -> T) : ImmutableList<T> {
        val size = readSmallInt()
        val list = ImmutableList.builderWithExpectedSize<T>(size)
        repeat(size) {
            list.add(reader())
        }
        return list.build()
    }

}
