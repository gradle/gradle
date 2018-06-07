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

import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * A lossy attribute container serializer. It's lossy because it doesn't preserve the attribute
 * types: it will serialize the contents as strings, and read them as strings, only for reporting
 * purposes.
 */
public class FullAttributeContainerSerializer implements AttributeContainerSerializer {
    private final ImmutableAttributesFactory attributesFactory;

    private static final byte STRING_ATTRIBUTE = 1;
    private static final byte BOOLEAN_ATTRIBUTE = 2;
    private static final byte SERIALIZED_ATTRIBUTE = 3;

    public FullAttributeContainerSerializer(ImmutableAttributesFactory attributesFactory) {
        this.attributesFactory = attributesFactory;
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
            } else if (type == STRING_ATTRIBUTE){
                String value = decoder.readString();
                attributes = attributesFactory.concat(attributes, Attribute.of(name, String.class), value);
            } else if (type == SERIALIZED_ATTRIBUTE) {
                Object value;
                byte[] valueBytes = new byte[decoder.readInt()];
                decoder.readBytes(valueBytes);
                ByteArrayInputStream inputStream;
                try {
                    inputStream = new ByteArrayInputStream(valueBytes);
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    value = objectInputStream.readObject();
                } catch (ClassNotFoundException e) {
                    throw new GradleException("Unable to load attribute value", e);
                }
                // Ugly cast to make compiler happy - ideally it would notice that value and value.getClass() _must_ satisfy the contract
                attributes = attributesFactory.concat(attributes, Attribute.of(name, (Class<Object>) value.getClass()), value);
            }
        }
        return attributes;
    }

    @Override
    public void write(Encoder encoder, AttributeContainer container) throws IOException {
        encoder.writeSmallInt(container.keySet().size());
        for (Attribute<?> attribute : container.keySet()) {
            encoder.writeString(attribute.getName());
            if (attribute.getType().equals(Boolean.class)) {
                encoder.writeByte(BOOLEAN_ATTRIBUTE);
                encoder.writeBoolean((Boolean) container.getAttribute(attribute));
            } else if (attribute.getType().equals(String.class)){
                encoder.writeByte(STRING_ATTRIBUTE);
                encoder.writeString((String) container.getAttribute(attribute));
            } else {
                assert Serializable.class.isAssignableFrom(attribute.getType());
                Object attributeValue = container.getAttribute(attribute);
                encoder.writeByte(SERIALIZED_ATTRIBUTE);
                ByteArrayOutputStream outputStream;
                try {
                    outputStream = new ByteArrayOutputStream();
                    ObjectOutputStream objectStr = new ObjectOutputStream(outputStream);
                    objectStr.writeObject(attributeValue);
                    objectStr.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                byte[] bytes = outputStream.toByteArray();
                encoder.writeInt(bytes.length);
                encoder.writeBytes(bytes);
            }
        }
    }
}
