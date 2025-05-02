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

import com.google.common.base.Objects;
import org.gradle.internal.Cast;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Default implementation of {@link SerializerRegistry}.
 *
 * This class must be thread-safe because multiple tasks may be registering serializable classes concurrently, while other tasks are calling {@link #build(Class)}.
 */
@ThreadSafe
public class DefaultSerializerRegistry implements SerializerRegistry {
    private static final Comparator<Class<?>> CLASS_COMPARATOR = new Comparator<Class<?>>() {
        @Override
        public int compare(Class<?> o1, Class<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };
    private final Map<Class<?>, SerializerFactory<?>> serializerMap = new ConcurrentSkipListMap<Class<?>, SerializerFactory<?>>(CLASS_COMPARATOR);

    // We are using a ConcurrentHashMap here because:
    //   - We don't want to use a Set with CLASS_COMPARATOR, since that would treat two classes with the same name originating from different classloaders as identical, allowing only one in the Set.
    //   - ConcurrentHashMap.newKeySet() isn't available on Java 6, yet, and that is where this code needs to run.
    //   - CopyOnWriteArraySet has slower insert performance, since it is not hash based.
    private final Map<Class<?>, Boolean> javaSerialization = new ConcurrentHashMap<Class<?>, Boolean>();
    private final SerializerClassMatcherStrategy classMatcher;

    public DefaultSerializerRegistry() {
        this(true);
    }

    public DefaultSerializerRegistry(boolean supportClassHierarchy) {
        this.classMatcher = supportClassHierarchy ? SerializerClassMatcherStrategy.HIERARCHY : SerializerClassMatcherStrategy.STRICT;
    }

    @Override
    public <T> void register(Class<T> implementationType, final Serializer<T> serializer) {
        registerWithFactory(implementationType, new InstanceBasedSerializerFactory<T>(serializer));
    }

    protected <T> void registerWithFactory(Class<T> implementationType, final SerializerFactory<T> serializerProvider) {
        serializerMap.put(implementationType, serializerProvider);
    }

    @Override
    public <T> void useJavaSerialization(Class<T> implementationType) {
        javaSerialization.put(implementationType, Boolean.TRUE);
    }

    @Override
    public boolean canSerialize(Class<?> baseType) {
        for (Class<?> candidate : serializerMap.keySet()) {
            if (classMatcher.matches(baseType, candidate)) {
                return true;
            }
        }
        for (Class<?> candidate : javaSerialization.keySet()) {
            if (classMatcher.matches(baseType, candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> Serializer<T> build(Class<T> baseType) {
        Map<Class<?>, SerializerFactory<?>> matches = new LinkedHashMap<Class<?>, SerializerFactory<?>>();
        for (Map.Entry<Class<?>, SerializerFactory<?>> entry : serializerMap.entrySet()) {
            if (baseType.isAssignableFrom(entry.getKey())) {
                matches.put(entry.getKey(), entry.getValue());
            }
        }
        Set<Class<?>> matchingJavaSerialization = new LinkedHashSet<Class<?>>();
        for (Class<?> candidate : javaSerialization.keySet()) {
            if (baseType.isAssignableFrom(candidate)) {
                matchingJavaSerialization.add(candidate);
            }
        }
        if (matches.isEmpty() && matchingJavaSerialization.isEmpty()) {
            throw new IllegalArgumentException(String.format("Don't know how to serialize objects of type %s.", baseType.getName()));
        }
        if (matches.size() == 1 && matchingJavaSerialization.isEmpty()) {
            return Cast.uncheckedNonnullCast(matches.values().iterator().next().serializerInstance());
        }
        return new TaggedTypeSerializer<T>(matches, matchingJavaSerialization);
    }

    private static class TypeInfo {
        final int tag;
        final boolean useForSubtypes;
        final Serializer<?> serializer;

        private TypeInfo(int tag, boolean useForSubtypes, Serializer<?> serializer) {
            this.tag = tag;
            this.useForSubtypes = useForSubtypes;
            this.serializer = serializer;
        }
    }

    private static class TaggedTypeSerializer<T> extends AbstractSerializer<T> {
        private static final int JAVA_TYPE = 1; // Reserve 0 for null (to be added later)
        private static final TypeInfo JAVA_SERIALIZATION = new TypeInfo(JAVA_TYPE, true, new DefaultSerializer<Object>());
        private final Map<Class<?>, TypeInfo> serializersByType = new HashMap<Class<?>, TypeInfo>();
        private final Map<Class<?>, TypeInfo> typeHierarchies = new HashMap<Class<?>, TypeInfo>();
        private final TypeInfo[] serializersByTag;

        TaggedTypeSerializer(Map<Class<?>, SerializerFactory<?>> serializerMap, Set<Class<?>> javaSerialization) {
            serializersByTag = new TypeInfo[2 + serializerMap.size()];
            serializersByTag[JAVA_TYPE] = JAVA_SERIALIZATION;
            int nextTag = 2;
            for (Map.Entry<Class<?>, SerializerFactory<?>> entry : serializerMap.entrySet()) {
                add(nextTag, entry.getKey(), entry.getValue().serializerInstance());
                nextTag++;
            }
            for (Class<?> type : javaSerialization) {
                serializersByType.put(type, JAVA_SERIALIZATION);
                typeHierarchies.put(type, JAVA_SERIALIZATION);
            }
        }

        private void add(int tag, Class<?> type, Serializer<?> serializer) {
            TypeInfo typeInfo = new TypeInfo(tag, type.equals(Throwable.class), serializer);
            serializersByType.put(type, typeInfo);
            serializersByTag[typeInfo.tag] = typeInfo;
            if (typeInfo.useForSubtypes) {
                typeHierarchies.put(type, typeInfo);
            }
        }

        @Override
        public T read(Decoder decoder) throws Exception {
            int tag = decoder.readSmallInt();
            TypeInfo typeInfo = tag >= serializersByTag.length ? null : serializersByTag[tag];
            if (typeInfo == null) {
                throw new IllegalArgumentException(String.format("Unexpected type tag %d found.", tag));
            }
            return Cast.uncheckedNonnullCast(typeInfo.serializer.read(decoder));
        }

        @Override
        public void write(Encoder encoder, T value) throws Exception {
            TypeInfo typeInfo = map(value.getClass());
            encoder.writeSmallInt(typeInfo.tag);
            Cast.<Serializer<T>>uncheckedNonnullCast(typeInfo.serializer).write(encoder, value);
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            TaggedTypeSerializer<?> rhs = (TaggedTypeSerializer<?>) obj;
            return Objects.equal(serializersByType, rhs.serializersByType)
                && Objects.equal(typeHierarchies, rhs.typeHierarchies)
                && Arrays.equals(serializersByTag, rhs.serializersByTag);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), serializersByType, typeHierarchies, Arrays.hashCode(serializersByTag));
        }

        private TypeInfo map(Class<?> valueType) {
            TypeInfo typeInfo = serializersByType.get(valueType);
            if (typeInfo != null) {
                return typeInfo;
            }
            for (Map.Entry<Class<?>, TypeInfo> entry : typeHierarchies.entrySet()) {
                if (entry.getKey().isAssignableFrom(valueType)) {
                    return entry.getValue();
                }
            }
            throw new IllegalArgumentException(String.format("Don't know how to serialize an object of type %s.", valueType.getName()));
        }
    }

    private interface SerializerClassMatcherStrategy {
        SerializerClassMatcherStrategy STRICT = new StrictSerializerMatcher();
        SerializerClassMatcherStrategy HIERARCHY = new HierarchySerializerMatcher();

        boolean matches(Class<?> baseType, Class<?> candidate);

    }

    /**
     * Serializer wrapper, that allows specific instance to be created when they cannot be shared or reused.
     *
     * @param <S> The type supported by the serializer
     */
    protected interface SerializerFactory<S> {
        Serializer<S> serializerInstance();
    }

    private static class InstanceBasedSerializerFactory<S> implements SerializerFactory<S> {

        private final Serializer<S> instance;

        private InstanceBasedSerializerFactory(Serializer<S> instance) {
            this.instance = instance;
        }

        @Override
        public Serializer<S> serializerInstance() {
            return instance;
        }
    }

    private static final class HierarchySerializerMatcher implements SerializerClassMatcherStrategy {
        @Override
        public boolean matches(Class<?> baseType, Class<?> candidate) {
            return baseType.isAssignableFrom(candidate);
        }
    }

    private static class StrictSerializerMatcher implements SerializerClassMatcherStrategy {
        @Override
        public boolean matches(Class<?> baseType, Class<?> candidate) {
            return baseType.equals(candidate);
        }
    }
}
