/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import com.google.common.collect.Iterators;

import java.util.ArrayDeque;
import java.util.Queue;

public abstract class AbstractNestedPropertyContext<T extends PropertyNode<T>> implements NestedPropertyContext<T> {

    /**
     * Successively iterates nested properties of an initial {@link PropertyNode}.
     *
     * All non-iterable {@link PropertyNode}s found by this process are collected via {@link NestedPropertyContext#addNested(PropertyNode)}.
     */
    public static <T extends PropertyNode<T>> void collectNestedProperties(T initial, NestedPropertyContext<T> context) {
        Queue<T> queue = new ArrayDeque<T>();
        queue.add(initial);

        while (!queue.isEmpty()) {
            T nestedNode = queue.remove();
            if (context.isIterable(nestedNode)) {
                Iterators.addAll(queue, nestedNode.getIterator());
            } else {
                context.addNested(nestedNode);
            }
        }
    }

    private final PropertyMetadataStore metadataStore;

    public AbstractNestedPropertyContext(PropertyMetadataStore metadataStore) {
        this.metadataStore = metadataStore;
    }

    @Override
    public boolean isIterable(T node) {
        return !node.isRoot()
            && node.isIterable()
            && !metadataStore.getTypeMetadata(node.getBeanClass()).isAnnotated();
    }
}
