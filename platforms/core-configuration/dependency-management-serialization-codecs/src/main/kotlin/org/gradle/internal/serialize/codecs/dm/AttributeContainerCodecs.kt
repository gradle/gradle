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

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.state.Managed
import org.gradle.internal.state.ManagedFactoryRegistry


class AttributeContainerCodec(
    private val attributesFactory: AttributesFactory,
    private val managedFactories: ManagedFactoryRegistry
) : Codec<AttributeContainer> {

    override suspend fun WriteContext.encode(value: AttributeContainer) {
        writeAttributes(value)
    }

    override suspend fun ReadContext.decode(): AttributeContainer? =
        readAttributesUsing(attributesFactory, managedFactories)
}


class ImmutableAttributesCodec(
    private val attributesFactory: AttributesFactory,
    private val managedFactories: ManagedFactoryRegistry
) : Codec<ImmutableAttributes> {

    override suspend fun WriteContext.encode(value: ImmutableAttributes) {
        encodePreservingSharedIdentityOf(value) {
            writeAttributes(it)
        }
    }

    override suspend fun ReadContext.decode(): ImmutableAttributes =
        decodePreservingSharedIdentity {
            readAttributesUsing(attributesFactory, managedFactories).asImmutable()
        }
}


private
suspend fun WriteContext.writeAttributes(container: AttributeContainer) {
    writeCollection(container.keySet()) { attribute ->
        writeAttribute(attribute)
        val value = container.getAttribute(attribute)
        writeAttributeValue(value)
    }
}


private
suspend fun ReadContext.readAttributesUsing(
    attributesFactory: AttributesFactory,
    managedFactories: ManagedFactoryRegistry
): AttributeContainerInternal =
    attributesFactory.mutable().apply {
        readCollection {
            val attribute = readAttribute()
            val value = readAttributeValue(managedFactories)
            attribute(attribute, value)
        }
    }


private
suspend fun WriteContext.writeAttributeValue(value: Any?) {
    if (value is Managed) {
        writeBoolean(true)
        // TODO: consider introducing a ManagedCodec
        writeManaged(value)
    } else {
        writeBoolean(false)
        write(value)
    }
}


private
suspend fun ReadContext.readAttributeValue(managedFactories: ManagedFactoryRegistry): Any =
    if (readBoolean()) {
        // TODO: consider introducing a ManagedCodec
        readManaged(managedFactories)
    } else {
        readNonNull()
    }


private
fun WriteContext.writeAttribute(attribute: Attribute<*>) {
    writeString(attribute.name)
    writeClass(attribute.type)
}


private
fun ReadContext.readAttribute(): Attribute<Any> {
    val name = readString()
    val type = readClass()
    return Attribute.of(name, type.uncheckedCast())
}


private
suspend fun WriteContext.writeManaged(value: Managed) {
    writeSmallInt(value.factoryId)
    writeClass(value.publicType())
    write(value.unpackState())
}


private
suspend fun ReadContext.readManaged(managedFactories: ManagedFactoryRegistry): Any {
    val factoryId = readSmallInt()
    val type = readClass()
    val state = read()
    return managedFactories.lookup(factoryId).fromState(type, state).let {
        require(it != null) {
            "Failed to recreate managed value of type $type from state $state"
        }
        it
    }
}
