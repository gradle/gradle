/*
 * Copyright 2022 the original author or authors.
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

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;

/**
 * A generalized type metadata walker for traversing annotated types and instances using their {@link TypeMetadata}.
 *
 * During the walk we first visit the root (the type or instance passed to {@link #walk(Object, TypeMetadataVisitor)},
 * and then the properties are visited in depth-first order.
 * Nested properties are marked with a nested annotation and are inspected for child properties that can be
 * "leaf" annotated properties, or other nested properties.
 * The {@link TypeMetadataStore} associated with the walker determines which non-nested property annotations are recognized
 * during the walk.
 * Nested {@code Map}s, {@code Iterable}s are resolved as child properties.
 * Nested iterables and maps can be further nested, i.e. {@code Map<String, Iterable<Iterable<String>>>} is supported.
 * Nested {@link Provider}s are unpacked, and the provided type is traversed transparently.
 */
public interface TypeMetadataWalker<T, V extends TypeMetadataWalker.TypeMetadataVisitor<T>> {

    /**
     * A factory method for a walker that can visit the property hierarchy of an instance.
     *
     * When visiting a nested property, child properties are discovered using the type of the
     * return property value. This can be a more specific type than the return type of the property's
     * getter method (and can declare additional child properties).
     *
     * Instance walker will throw {@link IllegalStateException} in case a nested property cycle is detected.
     */
    static InstanceMetadataWalker instanceWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.InstanceTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }

    /**
     * A factory method for a walker that can visit property hierarchy declared by a type.
     *
     * Type walker can detect a nested property cycle and stop walking the path with a cycle, no exception is thrown.
     */
    static StaticMetadataWalker typeWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.StaticTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }

    void walk(T root, V visitor);

    interface StaticMetadataWalker extends TypeMetadataWalker<TypeToken<?>, StaticMetadataVisitor> {}

    interface InstanceMetadataWalker extends TypeMetadataWalker<Object, InstanceMetadataVisitor> {}

    interface TypeMetadataVisitor<T> {
        void visitRoot(TypeMetadata typeMetadata, T value);
        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, T value);
    }

    interface StaticMetadataVisitor extends TypeMetadataVisitor<TypeToken<?>> {}

    interface InstanceMetadataVisitor extends TypeMetadataVisitor<Object> {
        @Override
        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, @Nullable Object value);
        void visitNestedUnpackingError(String qualifiedName, Exception e);
        void visitLeaf(Object parent, String qualifiedName, PropertyMetadata propertyMetadata);
    }
}
