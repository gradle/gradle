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

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.readNonNull
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.state.Managed
import org.gradle.internal.state.ManagedFactory


internal
class AttributeContainerCodec(
    private val attributesFactory: ImmutableAttributesFactory,
    private val managedFactory: ManagedFactory
) : Codec<AttributeContainer> {

    override suspend fun WriteContext.encode(value: AttributeContainer) {
        writeAttributes(value)
    }

    override suspend fun ReadContext.decode(): AttributeContainer? =
        readAttributesUsing(attributesFactory, managedFactory)
}


internal
class ImmutableAttributesCodec(
    private val attributesFactory: ImmutableAttributesFactory,
    private val managedFactory: ManagedFactory
) : Codec<ImmutableAttributes> {

    override suspend fun WriteContext.encode(value: ImmutableAttributes) {
        writeAttributes(value)
    }

    override suspend fun ReadContext.decode(): ImmutableAttributes =
        readAttributesUsing(attributesFactory, managedFactory).asImmutable()
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
    attributesFactory: ImmutableAttributesFactory,
    managedFactory: ManagedFactory
): AttributeContainerInternal =
    attributesFactory.mutable().apply {
        readCollection {
            val attribute = readAttribute()
            val value = readAttributeValue(managedFactory)
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
suspend fun ReadContext.readAttributeValue(managedFactory: ManagedFactory): Any =
    if (readBoolean()) {
        // TODO: consider introducing a ManagedCodec
        readManaged(managedFactory)
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
    writeClass(value.publicType())
    write(value.unpackState())
}


private
suspend fun ReadContext.readManaged(managedFactory: ManagedFactory): Any {
    val type = readClass()
    val state = read()
    return managedFactory.fromState(type, state).let {
        require(it != null) {
            "Failed to recreate managed value of type $type from state $state"
        }
        it
    }
}
