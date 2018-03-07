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

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.gradle.api.internal.tasks.properties.PropertyMetadataStore;
import org.gradle.api.internal.tasks.properties.TypeMetadata;

import javax.annotation.Nullable;

abstract class BaseRuntimeBeanNode<T> extends RuntimeBeanNode {
    private static final Equivalence<RuntimeBeanNode> SAME_BEANS = Equivalence.identity().onResultOf(new Function<RuntimeBeanNode, Object>() {
        @Override
        public Object apply(RuntimeBeanNode input) {
            return input.getBean();
        }
    });

    private final T bean;

    protected BaseRuntimeBeanNode(@Nullable String propertyName, T bean, @Nullable RuntimeBeanNode parentNode, TypeMetadata typeMetadata) {
        super(propertyName, parentNode, typeMetadata);
        this.bean = Preconditions.checkNotNull(bean, "Null is not allowed as nested property '%s'", propertyName);
        checkCycles();
    }

    @Override
    public T getBean() {
        return bean;
    }

    private void checkCycles() {
        RuntimeBeanNode nodeCreatingCycle = findNodeCreatingCycle(this, SAME_BEANS);
        Preconditions.checkState(
            nodeCreatingCycle == null,
            "Cycles between nested beans are not allowed. Cycle detected between: '%s' and '%s'.",
            nodeCreatingCycle, this);
    }

    public RuntimeBeanNode createChildNode(String propertyName, @Nullable Object input, PropertyMetadataStore metadataStore) {
        String qualifiedPropertyName = getQualifiedPropertyName(propertyName);
        Object bean = Preconditions.checkNotNull(input, "Null is not allowed as nested property '%s'", qualifiedPropertyName);
        return RuntimeBeanNode.create(qualifiedPropertyName, this, bean, metadataStore);
    }
}
