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
import com.google.common.base.Preconditions;
import org.gradle.api.internal.tasks.properties.AbstractPropertyNode;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.TypeMetadata;

import javax.annotation.Nullable;
import java.util.Queue;

public abstract class RuntimeBeanNode<T> extends AbstractPropertyNode<Object> {

    private final T bean;

    protected RuntimeBeanNode(@Nullable RuntimeBeanNode<?> parentNode, @Nullable String propertyName, T bean, TypeMetadata typeMetadata) {
        super(parentNode, propertyName, typeMetadata);
        this.bean = Preconditions.checkNotNull(bean, "Null is not allowed as nested property '%s'", propertyName);
    }

    public T getBean() {
        return bean;
    }

    @Override
    protected Object getNodeValue() {
        return getBean();
    }

    public abstract void visitNode(PropertyVisitor visitor, Queue<RuntimeBeanNode<?>> queue, RuntimeBeanNodeFactory nodeFactory, ParameterValidationContext validationContext);

    public RuntimeBeanNode<?> createChildNode(String propertyName, @Nullable Object input, RuntimeBeanNodeFactory nodeFactory) {
        String qualifiedPropertyName = getQualifiedPropertyName(propertyName);
        Object bean = Preconditions.checkNotNull(input, "Null is not allowed as nested property '%s'", qualifiedPropertyName);
        return nodeFactory.create(this, qualifiedPropertyName, bean);
    }

    public void checkCycles(String propertyName, Object childBean) {
        AbstractPropertyNode<?> nodeCreatingCycle = findNodeCreatingCycle(childBean, Equivalence.identity());
        Preconditions.checkState(
            nodeCreatingCycle == null,
            "Cycles between nested beans are not allowed. Cycle detected between: '%s' and '%s'.",
            nodeCreatingCycle, propertyName);
    }
}

