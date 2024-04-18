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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class InstancePairTypeMetadataWalker extends AbstractTypeMetadataWalker<Pair<Object, Object>, InstancePairTypeMetadataWalker.InstancePairMetadataVisitor> {
    private InstancePairTypeMetadataWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation, Supplier<Map<Pair<Object, Object>, String>> nestedNodeToQualifiedNameMapFactory) {
        super(typeMetadataStore, nestedAnnotation, nestedNodeToQualifiedNameMapFactory);
    }

    public static InstancePairTypeMetadataWalker instancePairWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new InstancePairTypeMetadataWalker(typeMetadataStore, nestedAnnotation, HashMap::new);
    }

    @Override
    protected void walkLeaf(Pair<Object, Object> node, @Nullable String parentQualifiedName, InstancePairMetadataVisitor visitor, PropertyMetadata propertyMetadata) {
        visitor.visitLeaf(node, getQualifiedName(parentQualifiedName, propertyMetadata.getPropertyName()), propertyMetadata);
    }

    @Override
    protected void onNestedNodeCycle(@Nullable String firstOccurrenceQualifiedName, String secondOccurrenceQualifiedName) {
        throw new IllegalStateException(String.format("Cycles between nested beans are not allowed. Cycle detected between: '%s' and '%s'.", firstOccurrenceQualifiedName, secondOccurrenceQualifiedName));
    }

    @Override
    protected void walkNestedProvider(Pair<Object, Object> node, String qualifiedName, PropertyMetadata propertyMetadata, InstancePairMetadataVisitor visitor, boolean isElementOfCollection, Consumer<Pair<Object, Object>> handler) {
        throw new UnsupportedOperationException("Nested providers are not supported.");
    }

    @Override
    protected void walkNestedMap(Pair<Object, Object> node, String qualifiedName, BiConsumer<String, Pair<Object, Object>> handler) {
        throw new UnsupportedOperationException("Nested maps are not supported.");
    }

    @Override
    protected void walkNestedIterable(Pair<Object, Object> node, String qualifiedName, BiConsumer<String, Pair<Object, Object>> handler) {
        throw new UnsupportedOperationException("Nested iterables are not supported.");
    }

    @Override
    protected void walkNestedChild(Pair<Object, Object> parent, String childQualifiedName, PropertyMetadata propertyMetadata, InstancePairMetadataVisitor visitor, Consumer<Pair<Object, Object>> handler) {
        Pair<Object, Object> child;
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
    protected Pair<Object, Object> getChild(Pair<Object, Object> parent, PropertyMetadata property) {
        return Pair.of(property.getPropertyValue(parent.getLeft()), property.getPropertyValue(parent.getRight()));
    }

    @Override
    protected Class<?> resolveType(Pair<Object, Object> pair) {
        return pair.getLeft().getClass();
    }

    public interface InstancePairMetadataVisitor extends TypeMetadataVisitor<Pair<Object, Object>> {
        @Override
        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, @Nullable Pair<Object, Object> pair);
        void visitNestedUnpackingError(String qualifiedName, Exception e);
        void visitLeaf(Pair<Object, Object> parent, String qualifiedName, PropertyMetadata propertyMetadata);
    }

    private static TypeToken<?> unpackType(TypeToken<?> type) {
        while (Provider.class.isAssignableFrom(type.getRawType())) {
            type = JavaReflectionUtil.extractNestedType(Cast.uncheckedCast(type), Provider.class, 0);
        }
        return type;
    }
}
