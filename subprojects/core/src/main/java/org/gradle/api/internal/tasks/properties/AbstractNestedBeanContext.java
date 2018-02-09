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

public abstract class AbstractNestedBeanContext<T extends BeanNode<T>> implements NestedBeanContext<T> {

    private final PropertyMetadataStore metadataStore;

    public AbstractNestedBeanContext(PropertyMetadataStore metadataStore) {
        this.metadataStore = metadataStore;
    }

    @Override
    public boolean isIterable(T node) {
        return !node.isRoot() && Iterable.class.isAssignableFrom(node.getBeanClass()) && !metadataStore.getTypeMetadata(node.getBeanClass()).isAnnotated();
    }
}
