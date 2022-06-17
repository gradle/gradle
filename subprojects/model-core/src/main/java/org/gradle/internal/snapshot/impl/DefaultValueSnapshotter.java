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

package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.attributes.Attribute;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.ValueSnapshottingException;
import org.gradle.internal.state.Managed;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public class DefaultValueSnapshotter extends AbstractValueProcessor implements ValueSnapshotter {
    private final ValueVisitor<ValueSnapshot> valueSnapshotValueVisitor;

    public DefaultValueSnapshotter(
        List<ValueSnapshotterSerializerRegistry> valueSnapshotterSerializerRegistryList,
        ClassLoaderHierarchyHasher classLoaderHasher
    ) {
        super(valueSnapshotterSerializerRegistryList);
        this.valueSnapshotValueVisitor = new ValueSnapshotVisitor(classLoaderHasher);
    }

    @Override
    public ValueSnapshot snapshot(@Nullable Object value) throws ValueSnapshottingException {
        return processValue(value, valueSnapshotValueVisitor);
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshot candidate) throws ValueSnapshottingException {
        return candidate.snapshot(value, this);
    }

    private static class ValueSnapshotVisitor implements ValueVisitor<ValueSnapshot> {
        private final ClassLoaderHierarchyHasher classLoaderHasher;

        ValueSnapshotVisitor(ClassLoaderHierarchyHasher classLoaderHasher) {
            this.classLoaderHasher = classLoaderHasher;
        }

        @Override
        public ValueSnapshot nullValue() {
            return NullValueSnapshot.INSTANCE;
        }

        @Override
        public ValueSnapshot stringValue(String value) {
            return new StringValueSnapshot(value);
        }

        @Override
        public ValueSnapshot booleanValue(Boolean value) {
            return value.equals(Boolean.TRUE) ? BooleanValueSnapshot.TRUE : BooleanValueSnapshot.FALSE;
        }

        @Override
        public ValueSnapshot integerValue(Integer value) {
            return new IntegerValueSnapshot(value);
        }

        @Override
        public ValueSnapshot longValue(Long value) {
            return new LongValueSnapshot(value);
        }

        @Override
        public ValueSnapshot shortValue(Short value) {
            return new ShortValueSnapshot(value);
        }

        @Override
        public ValueSnapshot hashCode(HashCode value) {
            return new HashCodeSnapshot(value);
        }

        @Override
        public ValueSnapshot enumValue(Enum value) {
            return new EnumValueSnapshot(value);
        }

        @Override
        public ValueSnapshot classValue(Class<?> value) {
            return ImplementationSnapshot.of(value, classLoaderHasher);
        }

        @Override
        public ValueSnapshot fileValue(File value) {
            return new FileValueSnapshot(value);
        }

        @Override
        public ValueSnapshot attributeValue(Attribute<?> value) {
            return new AttributeDefinitionSnapshot(value, classLoaderHasher);
        }

        @Override
        public ValueSnapshot managedImmutableValue(Managed managed) {
            return new ImmutableManagedValueSnapshot(managed.publicType().getName(), (String) managed.unpackState());
        }

        @Override
        public ValueSnapshot managedValue(Managed value, ValueSnapshot state) {
            return new ManagedValueSnapshot(value.publicType().getName(), state);
        }

        @Override
        public ValueSnapshot fromIsolatable(Isolatable<?> value) {
            return value.asSnapshot();
        }

        @Override
        public ValueSnapshot gradleSerialized(Object value, byte[] serializedValue) {
            return new GradleSerializedValueSnapshot(classLoaderHasher.getClassLoaderHash(value.getClass().getClassLoader()), serializedValue);
        }

        @Override
        public ValueSnapshot javaSerialized(Object value, byte[] serializedValue) {
            return new JavaSerializedValueSnapshot(classLoaderHasher.getClassLoaderHash(value.getClass().getClassLoader()), serializedValue);
        }

        @Override
        public ValueSnapshot emptyArray(Class<?> arrayType) {
            return ArrayValueSnapshot.EMPTY;
        }

        @Override
        public ValueSnapshot array(ImmutableList<ValueSnapshot> elements, Class<?> arrayType) {
            return new ArrayValueSnapshot(elements);
        }

        @Override
        public ValueSnapshot emptyList() {
            return ListValueSnapshot.EMPTY;
        }

        @Override
        public ValueSnapshot list(ImmutableList<ValueSnapshot> elements) {
            return new ListValueSnapshot(elements);
        }

        @Override
        public ValueSnapshot set(ImmutableSet<ValueSnapshot> elements) {
            return new SetValueSnapshot(elements);
        }

        @Override
        public ValueSnapshot map(ImmutableList<MapEntrySnapshot<ValueSnapshot>> elements) {
            return new MapValueSnapshot(elements);
        }

        @Override
        public ValueSnapshot properties(ImmutableList<MapEntrySnapshot<ValueSnapshot>> elements) {
            return new MapValueSnapshot(elements);
        }
    }
}
