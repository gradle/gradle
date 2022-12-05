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

package org.gradle.internal.properties.bean;

import com.google.common.base.Equivalence;
import com.google.common.base.Preconditions;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import javax.annotation.Nullable;
import java.util.Queue;

public abstract class RuntimeBeanNode<T> {

    private final T bean;
    private final String propertyName;
    private final RuntimeBeanNode<T> parentNode;

    protected RuntimeBeanNode(@Nullable RuntimeBeanNode<T> parentNode, @Nullable String propertyName, T bean) {
        this.propertyName = propertyName;
        this.parentNode = parentNode;
        this.bean = Preconditions.checkNotNull(bean, "Null is not allowed as nested property '%s'", propertyName);
    }

    public T getBean() {
        return bean;
    }

    public abstract void visitNode(PropertyVisitor visitor, Queue<RuntimeBeanNode<?>> queue, RuntimeBeanNodeFactory nodeFactory, TypeValidationContext validationContext);

    public RuntimeBeanNode<?> createChildNode(String propertyName, @Nullable Object input, RuntimeBeanNodeFactory nodeFactory) {
        String qualifiedPropertyName = getQualifiedPropertyName(propertyName);
        Object bean = Preconditions.checkNotNull(input, "Null is not allowed as nested property '%s'", qualifiedPropertyName);
        return nodeFactory.create(this, qualifiedPropertyName, bean);
    }

    @Nullable
    public String getPropertyName() {
        return propertyName;
    }

    protected String getQualifiedPropertyName(String childPropertyName) {
        return propertyName == null ? childPropertyName : propertyName + "." + childPropertyName;
    }

    public void checkCycles(String propertyName, T childBean) {
        RuntimeBeanNode<?> nodeCreatingCycle = findNodeCreatingCycle(childBean, Equivalence.identity());
        Preconditions.checkState(
            nodeCreatingCycle == null,
            "Cycles between nested beans are not allowed. Cycle detected between: '%s' and '%s'.",
            nodeCreatingCycle, propertyName);
    }

    @Nullable
    private RuntimeBeanNode<T> findNodeCreatingCycle(T childValue, Equivalence<? super T> nodeEquivalence) {
        if (nodeEquivalence.equivalent(getBean(), childValue)) {
            return this;
        }
        if (parentNode == null) {
            return null;
        }
        return parentNode.findNodeCreatingCycle(childValue, nodeEquivalence);
    }

    @Override
    public String toString() {
        //noinspection ConstantConditions
        return propertyName == null ? "<root>" : getPropertyName();
    }
}
