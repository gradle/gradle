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

/***
 * A generalized Type Metadata Walker for traversing Gradle types or instances in a Depth-first order.
 *
 * A visited nodes are leaf nodes, i.e. properties, or an intermediate nodes, i.e. nested nodes.
 *
 * A property can be:
 *  - a property of a type/instance
 *  - an element of an iterable which is annotated with a nested annotation (iterables of iterables are supported)
 *  - a value of a map annotated which is annotated with a nested annotation (maps of maps are supported)
 *  - a property of a nested type/instance which is annotated with a nested annotation
 *
 * A nested node is a node tha contain properties or other nested nodes. It's marked with a special nested annotation,
 * normally {@link org.gradle.api.tasks.Nested}. A nested annotation can be passed as a parameter to a walker.
 * Note: Maps, Iterables and Providers annotated with the nested annotations are automatically flatten.
 *
 * TypeMetadataWalker uses {@link TypeMetadataStore} to read nodes' {@link TypeMetadata} and to further discover the tree properties.
 * Usually a TypeMetadataStore that supports well known Gradle annotations like {@link org.gradle.api.tasks.Input} should be used,
 * but in general any TypeMetadataStore implementation can be used.
 */
public interface TypeMetadataWalker<T> {

    /**
     * A factory method for a walker that can visit an instance.
     *
     * Instance walker will throw {@link org.gradle.api.GradleException} in case a nested property cycle is detected.
     */
    static TypeMetadataWalker<Object> instanceWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.InstanceTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }

    /**
     * A factory method for a walker that can visit a type.
     *
     * Type walker can detect a nested property cycle and stop walking the path with a cycle, no exception is thrown.
     */
    static TypeMetadataWalker<TypeToken<?>> typeWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.StaticTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }

    void walk(T root, NodeMetadataVisitor<T> visitor);

    interface NodeMetadataVisitor<T> {
        void visitRoot(TypeMetadata typeMetadata, T value);

        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, T value);

        void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<T> value);
    }
}
