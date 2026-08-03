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
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * A thread-safe and reusable attribute container serializer that will desugar typed attributes.
 *
 * Attributes that are of types different than {@code String} or {@code boolean} will be desugared
 * before serialization. The process requires the attribute type to implement {@link Named}.
 */
public class DesugaringAttributeContainerSerializer implements AttributeContainerSerializer {
    private final AttributesFactory attributesFactory;
    private final NamedObjectInstantiator namedObjectInstantiator;

    private static final byte STRING_ATTRIBUTE = 1;
    private static final byte BOOLEAN_ATTRIBUTE = 2;
    private static final byte DESUGARED_ATTRIBUTE = 3;
    private static final byte INTEGER_ATTRIBUTE = 4;
    private static final byte NUMBER_ATTRIBUTE = 5;

    public DesugaringAttributeContainerSerializer(AttributesFactory attributesFactory, NamedObjectInstantiator namedObjectInstantiator) {
        this.attributesFactory = attributesFactory;
        this.namedObjectInstantiator = namedObjectInstantiator;
    }

    @Override
    @NonNull
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
            } else if (type == INTEGER_ATTRIBUTE){
                int value = decoder.readInt();
                attributes = attributesFactory.concat(attributes, Attribute.of(name, Integer.class), value);
            } else if (type == NUMBER_ATTRIBUTE) {
                String className = decoder.readString();
                String value = decoder.readString();
                Class<Number> numberType = resolveNumberType(className);
                attributes = attributesFactory.concat(attributes, Attribute.of(name, numberType), parseNumber(numberType, value));
            } else if (type == DESUGARED_ATTRIBUTE) {
                String value = decoder.readString();
                attributes = attributesFactory.concat(attributes, Attribute.of(name, String.class), new CoercingStringValueSnapshot(value, namedObjectInstantiator));
            }
        }
        return attributes;
    }

    @SuppressWarnings("DataFlowIssue")
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
            } else if (attribute.getType().equals(Integer.class)){
                encoder.writeByte(INTEGER_ATTRIBUTE);
                encoder.writeInt((Integer) container.getAttribute(attribute));
            } else if (Number.class.isAssignableFrom(attribute.getType())) {
                // Any other Number subtype (Long, Double, BigDecimal, ...). Preserve the concrete
                // type and value as strings so the exact type can be reconstructed on read.
                Number attributeValue = (Number) container.getAttribute(attribute);
                encoder.writeByte(NUMBER_ATTRIBUTE);
                encoder.writeString(attribute.getType().getName());
                encoder.writeString(attributeValue.toString());
            } else {
                // Attribute.of only accepts String, Boolean, a Number subtype, or a Named subtype;
                // the first three are handled above, so this branch is reached only via Named subtypes.
                Named attributeValue = (Named) container.getAttribute(attribute);
                encoder.writeByte(DESUGARED_ATTRIBUTE);
                encoder.writeString(attributeValue.getName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Number> Class<T> resolveNumberType(String className) {
        try {
            return (Class<T>) Class.forName(className, false, DesugaringAttributeContainerSerializer.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot deserialize attribute value: number type '" + className + "' was not found", e);
        }
    }

    /**
     * Reconstructs a {@link Number} of the given concrete type from its {@link Number#toString()} form,
     * mirroring {@link #write}. Uses the type's {@code valueOf(String)} factory when present (covers
     * {@code Byte}, {@code Short}, {@code Integer}, {@code Long}, {@code Float}, {@code Double}) and
     * falls back to a {@code (String)} constructor (covers {@code BigInteger}, {@code BigDecimal}).
     */
    private static <T extends Number> T parseNumber(Class<T> numberType, String value) {
        Optional<Method> valueOfMethod = findValueOfMethodForType(numberType);
        try {
            if (valueOfMethod.isPresent()) {
                return numberType.cast(valueOfMethod.get().invoke(null, value));
            } else {
                Constructor<T> constructor = numberType.getConstructor(String.class);
                return constructor.newInstance(value);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException e) {
            throw new IllegalStateException("Cannot reconstruct attribute value '" + value + "' as " + numberType.getName() + ": no usable static 'valueOf(String)' factory or '" + numberType.getName() + "(String)' constructor", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Cannot reconstruct attribute value '" + value + "' as " + numberType.getName() + ": error invoking 'valueOf(String)'", e);
        }
    }

    private static Optional<Method> findValueOfMethodForType(Class<?> numberType) {
        try {
            Method valueOf = numberType.getMethod("valueOf", String.class);
            boolean usable = Modifier.isStatic(valueOf.getModifiers()) && numberType.isAssignableFrom(valueOf.getReturnType());
            return usable ? Optional.of(valueOf) : Optional.empty();
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
