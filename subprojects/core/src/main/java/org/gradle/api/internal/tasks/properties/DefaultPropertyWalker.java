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

package org.gradle.api.internal.tasks.properties;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.DefaultTaskInputPropertySpec;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.DeferredUtil;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.gradle.api.internal.tasks.TaskValidationContext.Severity.ERROR;

@NonNullApi
public class DefaultPropertyWalker implements PropertyWalker {

    private final PropertyMetadataStore propertyMetadataStore;

    public DefaultPropertyWalker(PropertyMetadataStore propertyMetadataStore) {
        this.propertyMetadataStore = propertyMetadataStore;
    }

    @Override
    public void visitProperties(PropertySpecFactory specFactory, PropertyVisitor visitor, Object bean) {
        Queue<PropertyNode> queue = new ArrayDeque<PropertyNode>();
        queue.add(new PropertyNode(null, bean));
        while (!queue.isEmpty()) {
            PropertyNode node = queue.remove();
            Object nested;
            try {
                nested = node.getBean();
            } catch (Exception e) {
                // No nested bean
                continue;
            }
            Set<PropertyMetadata> nestedTypeMetadata = propertyMetadataStore.getTypeMetadata(nested.getClass());
            if (nested instanceof Collection<?> && shouldBeTraversed(nestedTypeMetadata)) {
                Collection nestedBeans = (Collection) nested;
                int count = 0;
                for (Object nestedBean : nestedBeans) {
                    String nestedPropertyName = node.parentPropertyName + "$" + ++count;
                    Set<PropertyMetadata> typeMetadata = propertyMetadataStore.getTypeMetadata(nestedBean.getClass());
                    visitProperties(new PropertyNode(nestedPropertyName, nestedBean), typeMetadata, queue, visitor, specFactory);
                }
            } else {
                visitProperties(node, nestedTypeMetadata, queue, visitor, specFactory);
                // TODO: Add implementation property
            }
        }
    }

    private boolean shouldBeTraversed(Set<PropertyMetadata> nestedTypeMetadata) {
        for (PropertyMetadata propertyMetadata : nestedTypeMetadata) {
            if (propertyMetadata.getPropertyType() != null) {
                return false;
            }
        }
        return true;
    }

    private static void visitProperties(PropertyNode node, Set<PropertyMetadata> typeMetadata, Queue<PropertyNode> queue, PropertyVisitor visitor, PropertySpecFactory specFactory) {
        for (PropertyMetadata propertyMetadata : typeMetadata) {
            PropertyValueVisitor propertyValueVisitor = propertyMetadata.getPropertyValueVisitor();
            if (propertyValueVisitor == null) {
                continue;
            }
            String propertyName = node.getQualifiedPropertyName(propertyMetadata.getFieldName());
            Object bean = node.getBean();
            PropertyValue propertyValue = new DefaultPropertyValue(propertyName, propertyMetadata.getAnnotations(), bean, propertyMetadata.getMethod());
            propertyValueVisitor.visitPropertyValue(propertyValue, visitor, specFactory);
            if (propertyValue.isAnnotationPresent(Nested.class)) {
                addNestedClassProperty(propertyValue, visitor, specFactory, propertyName, propertyValue.isOptional());
                try {
                    Object nested = propertyValue.getValue();
                    if (nested != null) {
                        queue.add(new PropertyNode(propertyName, nested));
                    }
                } catch (Exception e) {
                    // No nested bean
                }
            }
        }
    }

    private static void addNestedClassProperty(PropertyValue propertyValue, PropertyVisitor visitor, PropertySpecFactory specFactory, String propertyName, boolean optional) {
        DefaultTaskInputPropertySpec propertySpec = specFactory.createInputPropertySpec(propertyName + ".class", new NestedPropertyValue(propertyValue));
        propertySpec.optional(optional);
        visitor.visitInputProperty(propertySpec);
    }

    private static class PropertyNode {
        private final String parentPropertyName;
        private final Object bean;

        public PropertyNode(@Nullable String parentPropertyName, Object bean) {
            this.parentPropertyName = parentPropertyName;
            this.bean = bean;
        }

        public Object getBean() {
            return bean;
        }

        public String getQualifiedPropertyName(String propertyName) {
            return parentPropertyName == null ? propertyName : parentPropertyName + "." + propertyName;
        }
    }

    private static class DefaultPropertyValue implements PropertyValue {
        private final String propertyName;
        private final List<Annotation> annotations;
        private final Object bean;
        private final Method method;
        private final Supplier<Object> valueSupplier = Suppliers.memoize(new Supplier<Object>() {
            @Override
            @Nullable
            public Object get() {
                Object value = DeprecationLogger.whileDisabled(new Factory<Object>() {
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
                return value instanceof Provider ? ((Provider<?>) value).getOrNull() : value;
            }
        });

        public DefaultPropertyValue(String propertyName, List<Annotation> annotations, Object bean, Method method) {
            this.propertyName = propertyName;
            this.annotations = ImmutableList.copyOf(annotations);
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
            return getAnnotation(annotationType) != null;
        }

        @Nullable
        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            for (Annotation annotation : annotations) {
                if (annotationType.equals(annotation.annotationType())) {
                    return annotationType.cast(annotation);
                }
            }
            return null;
        }

        @Override
        public boolean isOptional() {
            return isAnnotationPresent(Optional.class);
        }

        @Nullable
        @Override
        public Object getValue() {
            return valueSupplier.get();
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
                    context.recordValidationMessage(ERROR, String.format("No value has been specified for property '%s'.", propertyName));
                }
            } else {
                valueValidator.validate(propertyName, unpacked, context, ERROR);
            }
        }
    }

    private static class NestedPropertyValue implements ValidatingValue {
        private final PropertyValue propertyValue;

        public NestedPropertyValue(PropertyValue propertyValue) {
            this.propertyValue = propertyValue;
        }

        @Nullable
        @Override
        public Object call() {
            Object bean = propertyValue.getValue();
            return bean == null ? null : bean.getClass().getName();
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
            Object bean = propertyValue.getValue();
            if (bean == null) {
                if (!optional) {
                    String realPropertyName = propertyName.substring(0, propertyName.length() - ".class".length());
                    context.recordValidationMessage(ERROR, String.format("No value has been specified for property '%s'.", realPropertyName));
                }
            } else {
                valueValidator.validate(propertyName, bean, context, ERROR);
            }
        }
    }
}
