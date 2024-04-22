/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.properties.annotations;

import com.google.common.reflect.TypeToken;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.reflect.JavaReflectionUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A {@link TypeMetadataWalker} that walks a pair of instances.  These could be two instances of the same type or two instances of types with
 * a common supertype.  The walker will visit each property of the instances, providing an {@link InstancePair} for each property, along with
 * the {@link PropertyMetadata} for that property.
 */
public class InstancePairTypeMetadataWalker extends AbstractTypeMetadataWalker<InstancePairTypeMetadataWalker.InstancePair<?>, InstancePairTypeMetadataWalker.InstancePairMetadataVisitor> {
    private InstancePairTypeMetadataWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation, Supplier<Map<InstancePair<?>, String>> nestedNodeToQualifiedNameMapFactory) {
        super(typeMetadataStore, nestedAnnotation, nestedNodeToQualifiedNameMapFactory);
    }

    public static InstancePairTypeMetadataWalker instancePairWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new InstancePairTypeMetadataWalker(typeMetadataStore, nestedAnnotation, HashMap::new);
    }

    @Override
    protected void walkLeaf(InstancePair<?> node, @Nullable String parentQualifiedName, InstancePairMetadataVisitor visitor, PropertyMetadata propertyMetadata) {
        visitor.visitLeaf(node, getQualifiedName(parentQualifiedName, propertyMetadata.getPropertyName()), propertyMetadata);
    }

    @Override
    protected void onNestedNodeCycle(@Nullable String firstOccurrenceQualifiedName, String secondOccurrenceQualifiedName) {
        throw new IllegalStateException(String.format("Cycles between nested beans are not allowed. Cycle detected between: '%s' and '%s'.", firstOccurrenceQualifiedName, secondOccurrenceQualifiedName));
    }

    @Override
    protected void walkNestedProvider(InstancePair<?> node, String qualifiedName, PropertyMetadata propertyMetadata, InstancePairMetadataVisitor visitor, boolean isElementOfCollection, Consumer<InstancePair<?>> handler) {
        throw new UnsupportedOperationException("Nested providers are not supported.");
    }

    @Override
    protected void walkNestedMap(InstancePair<?> node, String qualifiedName, BiConsumer<String, InstancePair<?>> handler) {
        throw new UnsupportedOperationException("Nested maps are not supported.");
    }

    @Override
    protected void walkNestedIterable(InstancePair<?> node, String qualifiedName, BiConsumer<String, InstancePair<?>> handler) {
        throw new UnsupportedOperationException("Nested iterables are not supported.");
    }

    @Override
    protected void walkNestedChild(InstancePair<?> parent, String childQualifiedName, PropertyMetadata propertyMetadata, InstancePairMetadataVisitor visitor, Consumer<InstancePair<?>> handler) {
        InstancePair<?> child;
        try {
            child = getChild(parent, propertyMetadata);
        } catch (Exception ex) {
            visitor.visitNestedUnpackingError(childQualifiedName, ex);
            return;
        }
        if (child.getLeft() != null) {
            handler.accept(child);
        } else {
            TypeToken<?> getterType = propertyMetadata.getDeclaredType();
            TypeMetadata typeMetadata = getTypeMetadata(unpackType(getterType).getRawType());
            visitor.visitNested(typeMetadata, childQualifiedName, propertyMetadata, null);
        }
    }

    @Override
    @Nonnull
    protected InstancePair<?> getChild(InstancePair<?> parent, PropertyMetadata property) {
        return InstancePair.of(
            property.getDeclaredType().getRawType(),
            Cast.uncheckedCast(property.getPropertyValue(parent.getLeft())),
            Cast.uncheckedCast(property.getPropertyValue(parent.getRight()))
        );
    }

    @Override
    protected Class<?> resolveType(InstancePair<?> pair) {
        return pair.getCommonType();
    }

    public static class InstancePair<T> {
        final Pair<? extends T, ? extends T> pair;
        final Class<T> commonType;

        private InstancePair(Class<T> commonType, Pair<? extends T, ? extends T> pair) {
            this.pair = pair;
            this.commonType = commonType;
        }

        public static <T, L extends T, R extends T> InstancePair<T> of(Class<T> commonType, @Nullable L left, @Nullable R right) {
            return new InstancePair<>(commonType, Pair.of(left, right));
        }

        @Nullable
        public Object getLeft() {
            return pair.getLeft();
        }

        @Nullable
        public Object getRight() {
            return pair.getRight();
        }

        public Class<T> getCommonType() {
            return commonType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InstancePair<?> that = (InstancePair<?>) o;
            return Objects.equals(pair, that.pair) && Objects.equals(commonType, that.commonType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pair, commonType);
        }
    }

    public interface InstancePairMetadataVisitor extends TypeMetadataVisitor<InstancePair<?>> {
        @Override
        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, @Nullable InstancePair<?> pair);
        void visitNestedUnpackingError(String qualifiedName, Exception e);
        void visitLeaf(InstancePair<?> parent, String qualifiedName, PropertyMetadata propertyMetadata);
    }

    private static TypeToken<?> unpackType(TypeToken<?> type) {
        while (Provider.class.isAssignableFrom(type.getRawType())) {
            type = JavaReflectionUtil.extractNestedType(Cast.uncheckedCast(type), Provider.class, 0);
        }
        return type;
    }
}
