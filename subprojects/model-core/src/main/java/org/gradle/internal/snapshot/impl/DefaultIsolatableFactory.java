/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.attributes.Attribute;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.state.Managed;
import org.gradle.internal.state.ManagedFactoryRegistry;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;

public class DefaultIsolatableFactory extends AbstractValueProcessor implements IsolatableFactory {

    private final ValueVisitor<Isolatable<?>> isolatableValueVisitor;

    public DefaultIsolatableFactory(
        ClassLoaderHierarchyHasher classLoaderHasher,
        ManagedFactoryRegistry managedFactoryRegistry
    ) {
        super(Collections.emptyList());
        this.isolatableValueVisitor = new IsolatableVisitor(classLoaderHasher, managedFactoryRegistry);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Isolatable<T> isolate(@Nullable T value) {
        try {
            return (Isolatable<T>) processValue(value, isolatableValueVisitor);
        } catch (Throwable t) {
            throw new IsolationException(value, t);
        }
    }

    private static class IsolatableVisitor implements ValueVisitor<Isolatable<?>> {
        private final ClassLoaderHierarchyHasher classLoaderHasher;
        private final ManagedFactoryRegistry managedFactoryRegistry;

        IsolatableVisitor(ClassLoaderHierarchyHasher classLoaderHasher, ManagedFactoryRegistry managedFactoryRegistry) {
            this.classLoaderHasher = classLoaderHasher;
            this.managedFactoryRegistry = managedFactoryRegistry;
        }

        @Override
        public Isolatable<?> nullValue() {
            return NullValueSnapshot.INSTANCE;
        }

        @Override
        public Isolatable<?> stringValue(String value) {
            return new StringValueSnapshot(value);
        }

        @Override
        public Isolatable<?> booleanValue(Boolean value) {
            return value.equals(Boolean.TRUE) ? BooleanValueSnapshot.TRUE : BooleanValueSnapshot.FALSE;
        }

        @Override
        public Isolatable<?> integerValue(Integer value) {
            return new IntegerValueSnapshot(value);
        }

        @Override
        public Isolatable<?> longValue(Long value) {
            return new LongValueSnapshot(value);
        }

        @Override
        public Isolatable<?> shortValue(Short value) {
            return new ShortValueSnapshot(value);
        }

        @Override
        public Isolatable<?> hashCode(HashCode value) {
            return new HashCodeSnapshot(value);
        }

        @Override
        public Isolatable<?> enumValue(Enum value) {
            return new IsolatedEnumValueSnapshot(value);
        }

        @Override
        public Isolatable<?> classValue(Class<?> value) {
            throw new IsolationException(value);
        }

        @Override
        public Isolatable<?> fileValue(File value) {
            return new FileValueSnapshot(value);
        }

        @Override
        public Isolatable<?> attributeValue(Attribute<?> value) {
            return new AttributeDefinitionSnapshot(value, classLoaderHasher);
        }

        @Override
        public Isolatable<?> managedImmutableValue(Managed managed) {
            return new IsolatedImmutableManagedValue(managed, managedFactoryRegistry);
        }

        @Override
        public Isolatable<?> managedValue(Managed value, Isolatable<?> state) {
            return new IsolatedManagedValue(value.publicType(), managedFactoryRegistry.lookup(value.getFactoryId()), state);
        }

        @Override
        public Isolatable<?> fromIsolatable(Isolatable<?> value) {
            return value;
        }

        @Override
        public Isolatable<?> gradleSerialized(Object value, byte[] serializedValue) {
            throw new UnsupportedOperationException("Isolating values of type '" + value.getClass().getSimpleName() + "' is not supported");
        }

        @Override
        public Isolatable<?> javaSerialized(Object value, byte[] serializedValue) {
            return new IsolatedJavaSerializedValueSnapshot(classLoaderHasher.getClassLoaderHash(value.getClass().getClassLoader()), serializedValue, value.getClass());
        }

        @Override
        public Isolatable<?> emptyArray(Class<?> arrayType) {
            return IsolatedArray.empty(arrayType);
        }

        @Override
        public Isolatable<?> array(ImmutableList<Isolatable<?>> elements, Class<?> arrayType) {
            return new IsolatedArray(elements, arrayType);
        }

        @Override
        public Isolatable<?> emptyList() {
            return IsolatedList.EMPTY;
        }

        @Override
        public Isolatable<?> list(ImmutableList<Isolatable<?>> elements) {
            return new IsolatedList(elements);
        }

        @Override
        public Isolatable<?> set(ImmutableSet<Isolatable<?>> elements) {
            return new IsolatedSet(elements);
        }

        @Override
        public Isolatable<?> map(ImmutableList<MapEntrySnapshot<Isolatable<?>>> elements) {
            return new IsolatedMap(elements);
        }

        @Override
        public Isolatable<?> properties(ImmutableList<MapEntrySnapshot<Isolatable<?>>> elements) {
            return new IsolatedProperties(elements);
        }
    }
}
