/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot;

import java.io.IOException;

/**
 * A lossy attribute container serializer. It's lossy because it doesn't preserve the attribute
 * types: it will serialize the contents as strings, and read them as strings, only for reporting
 * purposes.
 */
@ServiceScope(Scopes.BuildSession.class)
public class DesugaredAttributeContainerSerializer extends AbstractSerializer<AttributeContainer> implements AttributeContainerSerializer {
    private final ImmutableAttributesFactory attributesFactory;
    private final NamedObjectInstantiator instantiator;
    private static final byte STRING_ATTRIBUTE = 1;
    private static final byte BOOLEAN_ATTRIBUTE = 2;
    private static final byte INTEGER_ATTRIBUTE = 3;

    public DesugaredAttributeContainerSerializer(ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator instantiator) {
        this.attributesFactory = attributesFactory;
        this.instantiator = instantiator;
    }

    @Override
    public ImmutableAttributes read(Decoder decoder) throws IOException {
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        int count = decoder.readSmallInt();
        for (int i = 0; i < count; i++) {
            String name = decoder.readString();
            byte type = decoder.readByte();
            if (type == BOOLEAN_ATTRIBUTE) {
                attributes = attributesFactory.concat(attributes, Attribute.of(name, Boolean.class), decoder.readBoolean());
            } else if (type == INTEGER_ATTRIBUTE) {
                attributes = attributesFactory.concat(attributes, Attribute.of(name, Integer.class), decoder.readInt());
            } else {
                String value = decoder.readString();
                attributes = attributesFactory.concat(attributes, Attribute.of(name, String.class), new CoercingStringValueSnapshot(value, instantiator));
            }
        }
        return attributes;
    }

    @Override
    public void write(Encoder encoder, AttributeContainer container) throws IOException {
        encoder.writeSmallInt(container.keySet().size());
        for (Attribute<?> attribute : container.keySet()) {
            encoder.writeString(attribute.getName());
            Class<?> type = attribute.getType();
            if (type == Boolean.class) {
                encoder.writeByte(BOOLEAN_ATTRIBUTE);
                encoder.writeBoolean((Boolean) container.getAttribute(attribute));
            } else if (type == Integer.class) {
                encoder.writeByte(INTEGER_ATTRIBUTE);
                encoder.writeInt((Integer) container.getAttribute(attribute));
            } else {
                assert type == String.class : "Unexpected attribute type " + type + " : should be " + String.class.getSimpleName();
                encoder.writeByte(STRING_ATTRIBUTE);
                encoder.writeString((String) container.getAttribute(attribute));
            }
        }
    }
}
