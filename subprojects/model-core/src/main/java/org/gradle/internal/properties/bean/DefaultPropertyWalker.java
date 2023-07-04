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
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.provider.HasConfigurableValueInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.HasConfigurableValue;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.properties.annotations.TypeMetadataWalker;
import org.gradle.internal.properties.annotations.TypeMetadataWalker.InstanceMetadataWalker;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.snapshot.impl.ImplementationValue;
import org.gradle.internal.properties.annotations.NestedValidationUtil;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

@NonNullApi
public class DefaultPropertyWalker implements PropertyWalker {
    private final InstanceMetadataWalker walker;
    private final ImplementationResolver implementationResolver;
    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> handlers;

    public DefaultPropertyWalker(TypeMetadataStore typeMetadataStore, ImplementationResolver implementationResolver, Collection<PropertyAnnotationHandler> propertyHandlers) {
        this.walker = TypeMetadataWalker.instanceWalker(typeMetadataStore, Nested.class);
        this.implementationResolver = implementationResolver;
        this.handlers = propertyHandlers.stream().collect(toImmutableMap(PropertyAnnotationHandler::getAnnotationType, Function.identity()));
    }

    @Override
    public void visitProperties(Object bean, TypeValidationContext validationContext, PropertyVisitor visitor) {
        walker.walk(bean, new TypeMetadataWalker.InstanceMetadataVisitor() {
            @Override
            public void visitRoot(TypeMetadata typeMetadata, Object value) {
                typeMetadata.visitValidationFailures(null, validationContext);
            }

            @Override
            public void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, @Nullable Object value) {
                typeMetadata.visitValidationFailures(qualifiedName, validationContext);
                if (value != null) {
                    NestedValidationUtil.validateBeanType(validationContext, propertyMetadata.getPropertyName(), typeMetadata.getType());
                    ImplementationValue implementation = implementationResolver.resolveImplementation(value);
                    visitor.visitInputProperty(qualifiedName, new ImplementationPropertyValue(implementation), false);
                } else if (!propertyMetadata.isAnnotationPresent(Optional.class)) {
                    visitor.visitInputProperty(qualifiedName, PropertyValue.ABSENT, false);
                }
            }

            @Override
            public void visitNestedUnpackingError(String qualifiedName, Exception e) {
                visitor.visitInputProperty(qualifiedName, new InvalidValue(e), false);
            }

            @Override
            public void visitLeaf(Object parent, String qualifiedName, PropertyMetadata propertyMetadata) {
                PropertyValue cachedValue = new CachedPropertyValue(() -> propertyMetadata.getPropertyValue(parent), propertyMetadata.getDeclaredType().getRawType());
                PropertyAnnotationHandler handler = handlers.get(propertyMetadata.getPropertyType());
                if (handler == null) {
                    throw new IllegalStateException("Property handler should not be null for: " + propertyMetadata.getPropertyType());
                }
                handler.visitPropertyValue(qualifiedName, cachedValue, propertyMetadata, visitor);
            }
        });
    }

    private static class CachedPropertyValue implements PropertyValue {

        private final Supplier<Object> cachedInvoker;
        private final Class<?> declaredType;

        public CachedPropertyValue(Supplier<Object> supplier, Class<?> declaredType) {
            this.declaredType = declaredType;
            this.cachedInvoker = Suppliers.memoize(() -> DeprecationLogger.whileDisabled(supplier::get));
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
            if (isMap()) {
                return context -> {
                    Object dependency = cachedInvoker.get();
                    if (dependency instanceof Map) {
                        Map<Object, Object> map = Cast.uncheckedCast(dependency);
                        addCollectionDependencies(map.values(), context);
                    }
                };
            }
            if (isList() || isSet()) {
                return context -> {
                    Object dependency = cachedInvoker.get();
                    if (dependency instanceof Collection) {
                        addCollectionDependencies(Cast.uncheckedCast(dependency), context);
                    }
                };
            }
            return TaskDependencyContainer.EMPTY;
        }

        private static <T> void addCollectionDependencies(Collection<T> collection, TaskDependencyResolveContext context) {
            if (!collection.isEmpty()) {
                for (T element : collection) {
                    if (element instanceof TaskDependencyContainer) {
                        context.add(element);
                    }
                }
            }
        }

        @Override
        public void maybeFinalizeValue() {
            if (isConfigurable()) {
                Object value = cachedInvoker.get();
                ((HasConfigurableValueInternal) value).implicitFinalizeValue();
            }
        }

        private boolean isProvider() {
            return Provider.class.isAssignableFrom(declaredType);
        }

        private boolean isConfigurable() {
            return HasConfigurableValue.class.isAssignableFrom(declaredType);
        }

        private boolean isBuildable() {
            return Buildable.class.isAssignableFrom(declaredType);
        }

        private boolean isMap() {
            return Map.class.isAssignableFrom(declaredType);
        }

        private boolean isList() {
            return List.class.isAssignableFrom(declaredType);
        }

        private boolean isSet() {
            return Set.class.isAssignableFrom(declaredType);
        }

        @Nullable
        @Override
        public Object call() {
            return cachedInvoker.get();
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

    private static class InvalidValue implements PropertyValue {
        private final Exception exception;

        public InvalidValue(Exception exception) {
            this.exception = exception;
        }

        @Nullable
        @Override
        public Object call() {
            throw UncheckedException.throwAsUncheckedException(exception);
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
            return "INVALID: " + exception.getMessage();
        }
    }
}
