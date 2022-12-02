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

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

public interface TypeMetadataWalker<T, V extends TypeMetadataWalker.NodeMetadataVisitor<T>> {
    static InstanceMetadataWalker instanceWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.InstanceTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }

    static StaticMetadataWalker typeWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.StaticTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }

    void walk(T root, V visitor);

    interface StaticMetadataWalker extends TypeMetadataWalker<TypeToken<?>, StaticMetadataVisitor> {}
    interface InstanceMetadataWalker extends TypeMetadataWalker<Object, InstanceMetadataVisitor> {}

    interface NodeMetadataVisitor<T> {
        void visitRoot(TypeMetadata typeMetadata, T value);
        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, T value);
        void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<T> value);
    }

    interface StaticMetadataVisitor extends NodeMetadataVisitor<TypeToken<?>> {}
    interface InstanceMetadataVisitor extends NodeMetadataVisitor<Object> {
        void visitMissingNested(String qualifiedName, PropertyMetadata propertyMetadata);
        void visitUnpackNestedError(String qualifiedName, Exception e);
    }
}
