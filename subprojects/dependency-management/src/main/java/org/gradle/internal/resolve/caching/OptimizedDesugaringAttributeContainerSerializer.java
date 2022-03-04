/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve.caching;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Pair;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Optimized version of {@link DesugaringAttributeContainerSerializer}.
 */
public class OptimizedDesugaringAttributeContainerSerializer implements AttributeContainerSerializer {
    private final Map<Pair<Attribute<?>, Object>, Integer> writeIndex = new HashMap<>();
    private final List<ImmutableAttributes> readIndex = new ArrayList<>();
    private final ImmutableAttributesFactory attributesFactory;

    private static final byte STRING_ATTRIBUTE = 1;
    private static final byte BOOLEAN_ATTRIBUTE = 2;
    private static final byte INTEGER_ATTRIBUTE = 3;

    public OptimizedDesugaringAttributeContainerSerializer(ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator namedObjectInstantiator) {
        this.attributesFactory = attributesFactory;
    }

    @Override
    public ImmutableAttributes read(Decoder decoder) throws IOException {
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        int count = decoder.readSmallInt();
        for (int i = 0; i < count; i++) {
            int idx = decoder.readSmallInt();
            ImmutableAttributes next;
            if (idx == readIndex.size()) {
                // new entry
                next = readEntry(decoder);
                readIndex.add(next);
            } else {
                next = readIndex.get(idx);
            }
            attributes = attributesFactory.concat(attributes, next);
        }
        return attributes;
    }

    private ImmutableAttributes readEntry(Decoder decoder) throws IOException {
        String name = decoder.readString();
        byte type = decoder.readByte();
        if (type == BOOLEAN_ATTRIBUTE) {
            return attributesFactory.of(Attribute.of(name, Boolean.class), decoder.readBoolean());
        } else if (type == STRING_ATTRIBUTE) {
            String value = decoder.readString();
            return attributesFactory.of(Attribute.of(name, String.class), value);
        } else if (type == INTEGER_ATTRIBUTE) {
            int value = decoder.readInt();
            return attributesFactory.of(Attribute.of(name, Integer.class), value);
        } else {
            throw new IllegalStateException("Unknown attribute type: " + type);
        }
    }

    @Override
    public void write(Encoder encoder, AttributeContainer container) throws IOException {
        Set<Attribute<?>> keySet = container.keySet();
        encoder.writeSmallInt(keySet.size());
        for (Attribute<?> attribute : keySet) {
            Object attrValue = container.getAttribute(attribute);
            Pair<Attribute<?>, Object> key = Pair.of(attribute, attrValue);
            Integer idx = writeIndex.get(key);
            if (idx == null) {
                // new value
                encoder.writeSmallInt(writeIndex.size());
                writeIndex.put(key, writeIndex.size());
                writeEntry(encoder, attribute, attrValue);
            } else {
                // known value, only write index
                encoder.writeSmallInt(idx);
            }
        }
    }

    private void writeEntry(Encoder encoder, Attribute<?> attribute, Object attrValue) throws IOException {
        encoder.writeString(attribute.getName());
        Class<?> type = attribute.getType();
        if (type == Boolean.class) {
            encoder.writeByte(BOOLEAN_ATTRIBUTE);
            encoder.writeBoolean((Boolean) attrValue);
        } else if (type == String.class) {
            encoder.writeByte(STRING_ATTRIBUTE);
            encoder.writeString((String) attrValue);
        } else if (type == Integer.class) {
            encoder.writeByte(INTEGER_ATTRIBUTE);
            encoder.writeInt((Integer) attrValue);
        } else {
            Named attributeValue = (Named) attrValue;
            encoder.writeByte(STRING_ATTRIBUTE);
            encoder.writeString(attributeValue.getName());
        }
    }

    @Override
    public void reset() {
        writeIndex.clear();
        readIndex.clear();
    }
}
