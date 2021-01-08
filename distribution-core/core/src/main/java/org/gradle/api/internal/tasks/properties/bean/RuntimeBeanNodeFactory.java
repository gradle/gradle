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

package org.gradle.api.internal.tasks.properties.bean;

import org.gradle.api.internal.tasks.properties.TypeMetadata;
import org.gradle.api.internal.tasks.properties.TypeMetadataStore;

import java.util.Map;

public class RuntimeBeanNodeFactory {

    private final TypeMetadataStore metadataStore;

    public RuntimeBeanNodeFactory(TypeMetadataStore metadataStore) {
        this.metadataStore = metadataStore;
    }

    public RuntimeBeanNode<?> createRoot(Object bean) {
        return new RootRuntimeBeanNode(bean, metadataStore.getTypeMetadata(bean.getClass()));
    }

    public RuntimeBeanNode<?> create(RuntimeBeanNode parentNode, String propertyName, Object bean) {
        parentNode.checkCycles(propertyName, bean);
        TypeMetadata typeMetadata = metadataStore.getTypeMetadata(bean.getClass());
        if (!typeMetadata.hasAnnotatedProperties()) {
            if (bean instanceof Map<?, ?>) {
                return new MapRuntimeBeanNode(parentNode, propertyName, (Map<?, ?>) bean, typeMetadata);
            }
            if (bean instanceof Iterable<?>) {
                return new IterableRuntimeBeanNode(parentNode, propertyName, (Iterable<?>) bean, typeMetadata);
            }
        }
        return new NestedRuntimeBeanNode(parentNode, propertyName, bean, typeMetadata);
    }
}
