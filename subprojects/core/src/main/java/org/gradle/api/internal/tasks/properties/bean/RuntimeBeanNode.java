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

import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.properties.AbstractPropertyNode;
import org.gradle.api.internal.tasks.properties.NodeContext;
import org.gradle.api.internal.tasks.properties.PropertyMetadataStore;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.TypeMetadata;

import javax.annotation.Nullable;
import java.util.Map;

public abstract class RuntimeBeanNode extends AbstractPropertyNode {

    public static RuntimeBeanNode create(@Nullable String propertyName, Object bean, PropertyMetadataStore metadataStore) {
        TypeMetadata typeMetadata = metadataStore.getTypeMetadata(bean.getClass());
        if (propertyName != null && !typeMetadata.hasAnnotatedProperties()) {
            if (bean instanceof Map<?, ?>) {
                return new MapRuntimeBeanNode(propertyName, (Map<?, ?>) bean);
            }
            if (bean instanceof Iterable<?>) {
                return new IterableRuntimeBeanNode(propertyName, (Iterable<?>) bean);
            }
        }
        return new NestedRuntimeBeanNode(propertyName, bean);
    }

    protected RuntimeBeanNode(@Nullable String propertyName, Class<?> beanClass) {
        super(propertyName, beanClass);
    }

    public abstract Object getBean();

    public abstract void visitNode(PropertyVisitor visitor, PropertySpecFactory specFactory, NodeContext context, PropertyMetadataStore propertyMetadataStore);
}

