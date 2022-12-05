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

import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.snapshot.impl.ImplementationValue;

import java.util.Queue;

class NestedRuntimeBeanNode extends AbstractNestedRuntimeBeanNode {
    private final ImplementationValue implementation;

    public NestedRuntimeBeanNode(RuntimeBeanNode<Object> parentNode, String propertyName, Object bean, ImplementationValue implementation, TypeMetadata typeMetadata) {
        super(parentNode, propertyName, bean, typeMetadata);
        this.implementation = implementation;
    }

    @Override
    public void visitNode(PropertyVisitor visitor, Queue<RuntimeBeanNode<?>> queue, RuntimeBeanNodeFactory nodeFactory, TypeValidationContext validationContext) {
        visitImplementation(visitor);
        visitProperties(visitor, queue, nodeFactory, validationContext);
    }

    private void visitImplementation(PropertyVisitor visitor) {
        visitor.visitInputProperty(
            getPropertyName(),
            new ImplementationPropertyValue(implementation),
            false
        );
    }

    private static class ImplementationPropertyValue implements PropertyValue {

        private final ImplementationValue implementationValue;

        public ImplementationPropertyValue(ImplementationValue implementationValue) {
            this.implementationValue = implementationValue;
        }

        @Override
        public Object call() {
            return implementationValue;
        }

        @Override
        public TaskDependencyContainer getTaskDependencies() {
            // Ignore
            return TaskDependencyContainer.EMPTY;
        }

        @Override
        public void maybeFinalizeValue() {
            // Ignore
        }

        @Override
        public String toString() {
            return "Implementation: " + implementationValue;
        }
    }
}
