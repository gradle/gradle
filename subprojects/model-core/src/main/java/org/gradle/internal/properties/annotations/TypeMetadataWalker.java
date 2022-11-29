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
 * A generalized Type Metadata Walker for Gradle types or instances.
 *
 * A walker visits all properties of a type or an instance.
 */
public interface TypeMetadataWalker<T> {

    /**
     * A factory method for a walker that can visit an instance.
     * A walker is lightweight object so there is no need to cache it, but it holds no state, so it can be reused.
     *
     * Instance walker will throw {@link org.gradle.api.GradleException} in case a nested property cycle is detected.
     *
     * @param typeMetadataStore {@link TypeMetadataStore} that holds {@link TypeMetadata} for visited types.
     * @param nestedAnnotation An annotation that marks a nested property, normally the {@link org.gradle.api.tasks.Nested} annotation.
     * @return A new instance of TypeMetadataWalker.
     */
    static TypeMetadataWalker<Object> instanceWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.InstanceTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }

    /**
     * A factory method for a walker that can visit a type.
     * A walker is lightweight object so there is no need to cache it, but it holds no state, so it can be reused.
     *
     * Type walker can detect a nested property cycle and stop walking the path with a cycle, no exception is thrown.
     *
     * @param typeMetadataStore {@link TypeMetadataStore} that holds {@link TypeMetadata} for visited types.
     * @param nestedAnnotation an annotation that marks a nested property, normally the {@link org.gradle.api.tasks.Nested} annotation.
     * @return a new instance of TypeMetadataWalker.
     */
    static TypeMetadataWalker<TypeToken<?>> typeWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.StaticTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }


    /**
     * A start point to traverse a tree of nodes of a type or an instance.
     *
     * @param root A root type or an instance that we want to traverse.
     * @param visitor A {@link NodeMetadataVisitor} that visits the tree of nodes of a type or an instance.
     */
    void walk(T root, NodeMetadataVisitor<T> visitor);

    /**
     * A visitor that should be implemented to receive a callbacks when visiting a type or an instance.
     */
    interface NodeMetadataVisitor<T> {

        /**
         * A callback that will be called when a root is visited.
         *
         * @param typeMetadata A {@link TypeMetadata} of a root node.
         * @param value A value of a root.
         */
        void visitRoot(TypeMetadata typeMetadata, T value);

        /**
         * A callback that will be called when a nested node, i.e. node annotated with 'nestedAnnotation', is visited.
         *
         * @param typeMetadata A {@link TypeMetadata} of a nested node.
         * @param qualifiedName A fully qualified name of a nested node.
         * @param propertyMetadata A {@link PropertyMetadata} of a nested node.
         * @param value A value of a nested node.
         */
        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, T value);

        /**
         * A callback that will be called when a leaf is visited.
         *
         * @param qualifiedName A fully qualified name of a leaf.
         * @param propertyMetadata A {@link PropertyMetadata} of a leaf.
         * @param value A leaf value that is lazily invoked. A value supplied by a supplier can be null for instances, but not for types.
         */
        void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<T> value);
    }
}
