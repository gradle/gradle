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

import com.google.common.base.Suppliers;
import org.gradle.api.Buildable;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.provider.HasConfigurableValueInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.provider.HasConfigurableValue;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.properties.annotations.TypeMetadataWalker;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.snapshot.impl.ImplementationValue;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

@NonNullApi
public class DefaultPropertyWalker implements PropertyWalker {
    private final TypeMetadataWalker<Object> walker;
    private final ImplementationResolver implementationResolver;
    private final Map<Class<? extends Annotation>, PropertyValueHandler> handlers;

    public DefaultPropertyWalker(TypeMetadataStore typeMetadataStore, ImplementationResolver implementationResolver, PropertyValueHandler... handlers) {
        this.walker = TypeMetadataWalker.instanceWalker(typeMetadataStore, Nested.class);
        this.implementationResolver = implementationResolver;
        this.handlers = Arrays.stream(handlers).collect(toImmutableMap(PropertyValueHandler::getAnnotation, Function.identity()));
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
                PropertyValueHandler propertyValueHandler = handlers.get(propertyType);
                if (propertyValueHandler == null) {
                    throw new IllegalStateException("Property handler should not be null for: " + propertyType);
                }
                DynamicPropertyValue dynamicValue = new DynamicPropertyValue(value, propertyMetadata.getGetterMethod());
                propertyValueHandler.acceptVisitor(qualifiedName, dynamicValue, propertyMetadata, visitor);
            }
        });
    }


    private static class DynamicPropertyValue implements PropertyValue {

        private final Supplier<Object> supplier;
        private final Method method;
        private final Supplier<Object> cachedInvoker = Suppliers.memoize(new com.google.common.base.Supplier<Object>() {
            @Override
            @Nullable
            public Object get() {
                return DeprecationLogger.whileDisabled(() -> {
                    try {
                        return supplier.get();
                    } catch (Exception e) {
                        throw new GradleException(String.format("Could not call %s.%s()", method.getDeclaringClass().getSimpleName(), method.getName()), e);
                    }
                });
            }
        });

        public DynamicPropertyValue(Supplier<Object> supplier, Method method) {
            this.supplier = supplier;
            this.method = method;
        }

        @Override
        public TaskDependencyContainer getTaskDependencies() {
            if (isProvider()) {
                return (TaskDependencyContainer) cachedInvoker.get();
            }
            if (isBuildable()) {
                return context -> {
                    Object dependency = cachedInvoker.get();
                    if (dependency != null) {
                        context.add(dependency);
                    }
                };
            }
            return TaskDependencyContainer.EMPTY;
        }

        @Override
        public void maybeFinalizeValue() {
            if (isConfigurable()) {
                Object value = cachedInvoker.get();
                ((HasConfigurableValueInternal) value).implicitFinalizeValue();
            }
        }

        private boolean isProvider() {
            return Provider.class.isAssignableFrom(method.getReturnType());
        }

        private boolean isConfigurable() {
            return HasConfigurableValue.class.isAssignableFrom(method.getReturnType());
        }

        private boolean isBuildable() {
            return Buildable.class.isAssignableFrom(method.getReturnType());
        }

        @Nullable
        @Override
        public Object call() {
            return cachedInvoker.get();
        }

        @Override
        public String toString() {
            return "Method: " + method;
        }
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
