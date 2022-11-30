/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.properties.annotations.TypeMetadataWalker;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.snapshot.impl.ImplementationValue;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;

@NonNullApi
public class DefaultPropertyWalker implements PropertyWalker {
    private final TypeMetadataWalker<Object> walker;
    private final ImplementationResolver implementationResolver;

    public DefaultPropertyWalker(TypeMetadataStore typeMetadataStore, ImplementationResolver implementationResolver) {
        this.walker = TypeMetadataWalker.instanceWalker(typeMetadataStore, Nested.class);
        this.implementationResolver = implementationResolver;
    }

    @Override
    public void visitProperties(Object bean, TypeValidationContext validationContext, PropertyVisitor visitor) {
        walker.walk(bean, new TypeMetadataWalker.NodeMetadataVisitor<Object>() {
            @Override
            public void visitRoot(TypeMetadata typeMetadata, Object value) {
                // TODO What to do here?
            }

            @Override
            public void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, Object value) {
                ImplementationValue implementation = implementationResolver.resolveImplementation(value);
                visitor.visitInputProperty(
                    qualifiedName,
                    new ImplementationPropertyValue(implementation),
                    false
                );
            }

            @Override
            public void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<Object> value) {
                Class<? extends Annotation> propertyType = propertyMetadata.getPropertyType();
                whateverRegistry.getPropertyHandler(propertyType).acceptVisitor(visitor);
            }
        });
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
