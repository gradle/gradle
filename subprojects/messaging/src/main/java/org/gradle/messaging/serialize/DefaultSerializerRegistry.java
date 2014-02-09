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

package org.gradle.messaging.serialize;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class DefaultSerializerRegistry<T> implements SerializerRegistry<T> {
    private final Map<Class<?>, Serializer<?>> serializerMap = new TreeMap<Class<?>, Serializer<?>>(new Comparator<Class<?>>() {
        public int compare(Class<?> o1, Class<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    });

    public <U extends T> void register(Class<U> implementationType, Serializer<U> serializer) {
        serializerMap.put(implementationType, serializer);
    }

    public Serializer<T> build() {
        if (serializerMap.size() == 1) {
            return (Serializer<T>) serializerMap.values().iterator().next();
        }
        TaggedTypeSerializer<T> serializer = new TaggedTypeSerializer<T>();
        for (Map.Entry<Class<?>, Serializer<?>> entry : serializerMap.entrySet()) {
            serializer.add(entry.getKey(), entry.getValue());
        }
        return serializer;
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
        private final Map<Class<?>, TypeInfo> serializersByType = new HashMap<Class<?>, TypeInfo>();
        private final Map<Byte, TypeInfo> serializersByTag = new HashMap<Byte, TypeInfo>();

        private <T> void add(Class<?> type, Serializer<?> serializer) {
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
            Class<?> targetType = value instanceof Throwable ? Throwable.class : value.getClass();
            TypeInfo typeInfo = serializersByType.get(targetType);
            if (typeInfo == null) {
                throw new IllegalArgumentException(String.format("Don't know how to serialize an object of type %s.", value.getClass().getName()));
            }
            encoder.writeByte(typeInfo.tag);
            typeInfo.serializer.write(encoder, value);
        }
    }
}
