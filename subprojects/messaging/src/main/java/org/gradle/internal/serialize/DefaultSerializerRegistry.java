/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.serialize;

import java.util.*;

public class DefaultSerializerRegistry<T> implements SerializerRegistry<T> {
    private final Map<Class<?>, Serializer<?>> serializerMap = new TreeMap<Class<?>, Serializer<?>>(new Comparator<Class<?>>() {
        public int compare(Class<?> o1, Class<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    });
    private final Set<Class<?>> javaSerialization = new HashSet<Class<?>>();

    public <U extends T> void register(Class<U> implementationType, Serializer<U> serializer) {
        serializerMap.put(implementationType, serializer);
    }

    @Override
    public <U extends T> void useJavaSerialization(Class<U> implementationType) {
        javaSerialization.add(implementationType);
    }

    public Serializer<T> build() {
        if (serializerMap.size() == 1 && javaSerialization.isEmpty()) {
            return (Serializer<T>) serializerMap.values().iterator().next();
        }
        return new TaggedTypeSerializer<T>(serializerMap, javaSerialization);
    }

    private static class TypeInfo {
        final byte tag;
        final Serializer serializer;

        private TypeInfo(byte tag, Serializer serializer) {
            this.tag = tag;
            this.serializer = serializer;
        }
    }

    private static class TaggedTypeSerializer<T> implements Serializer<T> {
        private static final TypeInfo JAVA_SERIALIZATION = new TypeInfo((byte) 0, new DefaultSerializer<Object>());
        private final Map<Class<?>, TypeInfo> serializersByType = new HashMap<Class<?>, TypeInfo>();
        private final Map<Byte, TypeInfo> serializersByTag = new HashMap<Byte, TypeInfo>();
        private final Set<Class<?>> javaSerialization;

        public TaggedTypeSerializer(Map<Class<?>, Serializer<?>> serializerMap, Set<Class<?>> javaSerialization) {
            this.javaSerialization = new HashSet<Class<?>>(javaSerialization);
            serializersByTag.put(JAVA_SERIALIZATION.tag, JAVA_SERIALIZATION);
            for (Map.Entry<Class<?>, Serializer<?>> entry : serializerMap.entrySet()) {
                add(entry.getKey(), entry.getValue());
            }
        }

        private void add(Class<?> type, Serializer<?> serializer) {
            TypeInfo typeInfo = new TypeInfo((byte) serializersByTag.size(), serializer);
            serializersByType.put(type, typeInfo);
            serializersByTag.put(typeInfo.tag, typeInfo);
        }

        public T read(Decoder decoder) throws Exception {
            byte tag = decoder.readByte();
            TypeInfo typeInfo = serializersByTag.get(tag);
            if (typeInfo == null) {
                throw new IllegalArgumentException(String.format("Unexpected type tag %d found.", tag));
            }
            return (T) typeInfo.serializer.read(decoder);
        }

        public void write(Encoder encoder, T value) throws Exception {
            TypeInfo typeInfo = map(value.getClass());
            encoder.writeByte(typeInfo.tag);
            typeInfo.serializer.write(encoder, value);
        }

        private TypeInfo map(Class<?> valueType) {
            Class<?> targetType = Throwable.class.isAssignableFrom(valueType) ? Throwable.class : valueType;
            TypeInfo typeInfo = serializersByType.get(targetType);
            if (typeInfo != null) {
                return typeInfo;
            }
            for (Class<?> candidate : javaSerialization) {
                if (candidate.isAssignableFrom(targetType)) {
                    return JAVA_SERIALIZATION;
                }
            }
            throw new IllegalArgumentException(String.format("Don't know how to serialize an object of type %s.", valueType.getName()));
        }
    }
}
