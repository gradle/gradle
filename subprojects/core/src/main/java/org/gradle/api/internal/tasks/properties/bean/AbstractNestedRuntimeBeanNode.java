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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.internal.provider.ProducerAwareProperty;
import org.gradle.api.internal.provider.PropertyInternal;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.PropertyMetadata;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyValueVisitor;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.TypeMetadata;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.DeferredUtil;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Queue;

public abstract class AbstractNestedRuntimeBeanNode extends RuntimeBeanNode<Object> {
    protected AbstractNestedRuntimeBeanNode(@Nullable RuntimeBeanNode<?> parentNode, @Nullable String propertyName, Object bean, TypeMetadata typeMetadata) {
        super(parentNode, propertyName, bean, typeMetadata);
    }

    public void visitProperties(PropertyVisitor visitor, PropertySpecFactory specFactory, final Queue<RuntimeBeanNode<?>> queue, final RuntimeBeanNodeFactory nodeFactory) {
        TypeMetadata typeMetadata = getTypeMetadata();
        for (final PropertyMetadata propertyMetadata : typeMetadata.getPropertiesMetadata()) {
            PropertyValueVisitor propertyValueVisitor = propertyMetadata.getPropertyValueVisitor();
            if (propertyValueVisitor == null || !propertyValueVisitor.shouldVisit(visitor)) {
                continue;
            }
            String propertyName = getQualifiedPropertyName(propertyMetadata.getFieldName());
            PropertyValue propertyValue = new DefaultPropertyValue(propertyName, propertyMetadata, getBean(), propertyMetadata.getMethod());
            propertyValueVisitor.visitPropertyValue(propertyValue, visitor, specFactory, new BeanPropertyContext() {
                @Override
                public void addNested(String propertyName, Object bean) {
                    queue.add(nodeFactory.create(AbstractNestedRuntimeBeanNode.this, propertyName, bean));
                }
            });
        }
    }

    private static class DefaultPropertyValue implements PropertyValue {
        private final String propertyName;
        private final PropertyMetadata propertyMetadata;
        private final Object bean;
        private final Method method;
        private final Supplier<Object> valueSupplier = Suppliers.memoize(new Supplier<Object>() {
            @Override
            @Nullable
            public Object get() {
                return DeprecationLogger.whileDisabled(new Factory<Object>() {
                    public Object create() {
                        try {
                            return method.invoke(bean);
                        } catch (InvocationTargetException e) {
                            throw UncheckedException.throwAsUncheckedException(e.getCause());
                        } catch (Exception e) {
                            throw new GradleException(String.format("Could not call %s.%s() on %s", method.getDeclaringClass().getSimpleName(), method.getName(), bean), e);
                        }
                    }
                });
            }
        });

        public DefaultPropertyValue(String propertyName, PropertyMetadata propertyMetadata, Object bean, Method method) {
            this.propertyName = propertyName;
            this.propertyMetadata = propertyMetadata;
            this.bean = bean;
            this.method = method;
            method.setAccessible(true);
        }

        @Override
        public String getPropertyName() {
            return propertyName;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return propertyMetadata.isAnnotationPresent(annotationType);
        }

        @Nullable
        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return propertyMetadata.getAnnotation(annotationType);
        }

        @Override
        public boolean isOptional() {
            return isAnnotationPresent(Optional.class);
        }

        @Override
        public void attachProducer(Task producer) {
            if (Provider.class.isAssignableFrom(method.getReturnType())) {
                Object value = valueSupplier.get();
                if (value instanceof ProducerAwareProperty) {
                    ((ProducerAwareProperty) value).attachProducer(producer);
                }
            }
        }

        @Override
        public void maybeFinalizeValue() {
            if (Provider.class.isAssignableFrom(method.getReturnType())) {
                Object value = valueSupplier.get();
                if (value instanceof PropertyInternal) {
                    ((PropertyInternal) value).finalizeValueOnReadAndWarnAboutChanges();
                }
            }
        }

        @Nullable
        @Override
        public Object getValue() {
            Object value = valueSupplier.get();
            // Replace absent Provider with null.
            // This is required for allowing optional provider properties - all code which unpacks providers calls Provider.get() and would fail if an optional provider is passed.
            // Returning null from a Callable is ignored, and PropertyValue is a callable.
            if (value instanceof Provider && !((Provider<?>) value).isPresent()) {
                return null;
            }
            return value;
        }

        @Nullable
        @Override
        public Object call() {
            return getValue();
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
            Object unpacked = DeferredUtil.unpack(getValue());
            if (unpacked == null) {
                if (!optional) {
                    context.recordValidationMessage(String.format("No value has been specified for property '%s'.", propertyName));
                }
            } else {
                valueValidator.validate(propertyName, unpacked, context);
            }
        }
    }
}
