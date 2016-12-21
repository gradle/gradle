/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import groovy.lang.GroovyObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.options.OptionValues;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.Types;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class DefaultTaskClassValidatorExtractor implements TaskClassValidatorExtractor {
    // Avoid reflecting on classes we know we don't need to look at
    private static final Collection<Class<?>> IGNORED_SUPER_CLASSES = ImmutableSet.<Class<?>>of(
        ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class
    );

    private final static List<? extends PropertyAnnotationHandler> HANDLERS = Arrays.asList(
        new InputFilePropertyAnnotationHandler(),
        new InputDirectoryPropertyAnnotationHandler(),
        new InputFilesPropertyAnnotationHandler(),
        new OutputFilePropertyAnnotationHandler(),
        new OutputFilesPropertyAnnotationHandler(),
        new OutputDirectoryPropertyAnnotationHandler(),
        new OutputDirectoriesPropertyAnnotationHandler(),
        new InputPropertyAnnotationHandler(),
        new NestedBeanPropertyAnnotationHandler(),
        new NoOpPropertyAnnotationHandler(Inject.class),
        new NoOpPropertyAnnotationHandler(Console.class),
        new NoOpPropertyAnnotationHandler(Internal.class),
        new NoOpPropertyAnnotationHandler(OptionValues.class)
    );

    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> annotationHandlers;
    private final Multimap<Class<? extends Annotation>, Class<? extends Annotation>> annotationOverrides;

    public DefaultTaskClassValidatorExtractor(PropertyAnnotationHandler... customAnnotationHandlers) {
        this(Arrays.asList(customAnnotationHandlers));
    }

    public DefaultTaskClassValidatorExtractor(Iterable<? extends PropertyAnnotationHandler> customAnnotationHandlers) {
        Iterable<PropertyAnnotationHandler> allAnnotationHandlers = Iterables.concat(HANDLERS, customAnnotationHandlers);
        this.annotationHandlers = Maps.uniqueIndex(allAnnotationHandlers, new Function<PropertyAnnotationHandler, Class<? extends Annotation>>() {
            @Override
            public Class<? extends Annotation> apply(PropertyAnnotationHandler handler) {
                return handler.getAnnotationType();
            }
        });
        this.annotationOverrides = collectAnnotationOverrides(allAnnotationHandlers);
    }

    private static Multimap<Class<? extends Annotation>, Class<? extends Annotation>> collectAnnotationOverrides(Iterable<PropertyAnnotationHandler> allAnnotationHandlers) {
        ImmutableSetMultimap.Builder<Class<? extends Annotation>, Class<? extends Annotation>> builder = ImmutableSetMultimap.builder();
        for (PropertyAnnotationHandler handler : allAnnotationHandlers) {
            if (handler instanceof OverridingPropertyAnnotationHandler) {
                builder.put(((OverridingPropertyAnnotationHandler) handler).getOverriddenAnnotationType(), handler.getAnnotationType());
            }
        }
        return builder.build();
    }

    @Override
    public TaskClassValidator extractValidator(Class<? extends Task> type) {
        ImmutableSet.Builder<TaskPropertyInfo> validatedPropertiesBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<String> nonAnnotatedPropertiesBuilder = ImmutableSortedSet.naturalOrder();
        Queue<TypeEntry> queue = new ArrayDeque<TypeEntry>();
        queue.add(new TypeEntry(null, type));
        while (!queue.isEmpty()) {
            TypeEntry entry = queue.remove();
            parseProperties(entry.parent, entry.type, validatedPropertiesBuilder, nonAnnotatedPropertiesBuilder, queue);
        }
        return new TaskClassValidator(validatedPropertiesBuilder.build(), nonAnnotatedPropertiesBuilder.build());
    }

    private <T> void parseProperties(final TaskPropertyInfo parent, Class<T> type, ImmutableSet.Builder<TaskPropertyInfo> validatedPropertiesBuilder, ImmutableSet.Builder<String> nonAnnotatedPropertiesBuilder, Queue<TypeEntry> queue) {
        final Set<Class<? extends Annotation>> propertyTypeAnnotations = annotationHandlers.keySet();
        final Map<String, DefaultTaskPropertyActionContext> propertyContexts = Maps.newHashMap();
        Types.walkTypeHierarchy(type, IGNORED_SUPER_CLASSES, new Types.TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                Map<String, Field> fields = getFields(type);
                Method[] methods = type.getDeclaredMethods();
                for (Method method : methods) {
                    PropertyAccessorType accessorType = PropertyAccessorType.of(method);
                    if (accessorType == null || accessorType == PropertyAccessorType.SETTER || method.isBridge() || GroovyMethods.isObjectMethod(method)) {
                        continue;
                    }

                    String fieldName = accessorType.propertyNameFor(method);
                    Field field = fields.get(fieldName);
                    String propertyName = parent != null ? parent.getName() + '.' + fieldName : fieldName;

                    DefaultTaskPropertyActionContext propertyContext = propertyContexts.get(propertyName);
                    if (propertyContext == null) {
                        propertyContext = new DefaultTaskPropertyActionContext(propertyTypeAnnotations, parent, propertyName, method);
                        propertyContexts.put(propertyName, propertyContext);
                    }

                    final List<Annotation> declaredAnnotations = Lists.newArrayList(method.getDeclaredAnnotations());
                    if (field != null) {
                        propertyContext.setInstanceVariableField(field);
                        Collections.addAll(declaredAnnotations, field.getDeclaredAnnotations());
                    }

                    // Discard overridden property type annotations when an overriding annotation is also present
                    propertyContext.addAnnotations(Iterables.filter(declaredAnnotations, new Predicate<Annotation>() {
                        @Override
                        public boolean apply(Annotation input) {
                            Class<? extends Annotation> annotationType = input.annotationType();
                            if (!propertyTypeAnnotations.contains(annotationType)) {
                                return true;
                            }
                            for (Class<? extends Annotation> overridingAnnotation : annotationOverrides.get(annotationType)) {
                                for (Annotation declaredAnnotation : declaredAnnotations) {
                                    if (declaredAnnotation.annotationType().equals(overridingAnnotation)) {
                                        return false;
                                    }
                                }
                            }
                            return true;
                        }
                    }));
                }
            }
        });
        for (DefaultTaskPropertyActionContext propertyContext : propertyContexts.values()) {
            TaskPropertyInfo property = createProperty(propertyContext, nonAnnotatedPropertiesBuilder);
            if (property != null) {
                validatedPropertiesBuilder.add(property);
                Class<?> nestedType = propertyContext.getNestedType();
                if (nestedType != null) {
                    queue.add(new TypeEntry(property, nestedType));
                }
            }
        }
    }

    private static class TypeEntry {
        private final TaskPropertyInfo parent;
        private final Class<?> type;

        public TypeEntry(TaskPropertyInfo parent, Class<?> type) {
            this.parent = parent;
            this.type = type;
        }
    }

    private TaskPropertyInfo createProperty(DefaultTaskPropertyActionContext propertyContext, ImmutableSet.Builder<String> nonAnnotatedProperties) {
        Class<? extends Annotation> propertyType = propertyContext.getPropertyType();
        if (propertyType != null) {
            if (propertyContext.isAnnotationPresent(Optional.class)) {
                propertyContext.setOptional(true);
            }

            PropertyAnnotationHandler handler = annotationHandlers.get(propertyType);
            handler.attachActions(propertyContext);

            return propertyContext.createProperty();
        } else {
            nonAnnotatedProperties.add(propertyContext.getName());
            return null;
        }
    }

    private static Map<String, Field> getFields(Class<?> type) {
        Map<String, Field> fields = Maps.newHashMap();
        for (Field field : type.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
        return fields;
    }

    private static class DefaultTaskPropertyActionContext implements TaskPropertyActionContext {
        private final Set<Class<? extends Annotation>> propertyTypeAnnotations;
        private final TaskPropertyInfo parent;
        private final String name;
        private final Method method;
        private final List<Annotation> annotations = Lists.newArrayList();
        private Field instanceVariableField;
        private ValidationAction validationAction;
        private UpdateAction configureAction;
        private boolean optional;
        private Class<?> nestedType;
        private Class<? extends Annotation> propertyType;

        public DefaultTaskPropertyActionContext(Set<Class<? extends Annotation>> propertyTypeAnnotations, TaskPropertyInfo parent, String name, Method method) {
            this.propertyTypeAnnotations = propertyTypeAnnotations;
            this.parent = parent;
            this.name = name;
            this.method = method;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<? extends Annotation> getPropertyType() {
            return propertyType;
        }

        @Override
        public Class<?> getValueType() {
            return instanceVariableField != null
                ? instanceVariableField.getType()
                : method.getReturnType();
        }

        @Override
        public void addAnnotations(Iterable<? extends Annotation> declaredAnnotations) {
            for (Annotation annotation : declaredAnnotations) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                // Record the most specific property type annotation only
                if (propertyType == null && isPropertyTypeAnnotation(annotationType)) {
                    propertyType = annotationType;
                }
                // Record the most specific annotation only
                if (!isAnnotationPresent(annotation.getClass())) {
                    annotations.add(annotation);
                }
            }
        }

        private boolean isPropertyTypeAnnotation(Class<? extends Annotation> annotationType) {
            return propertyTypeAnnotations.contains(annotationType);
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            for (Annotation annotation : annotations) {
                if (annotationType.isAssignableFrom(annotation.getClass())) {
                    return Cast.uncheckedCast(annotation);
                }
            }
            return null;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return getAnnotation(annotationType) != null;
        }

        @Override
        public void setInstanceVariableField(Field instanceVariableField) {
            if (this.instanceVariableField == null && instanceVariableField != null) {
                this.instanceVariableField = instanceVariableField;
            }
        }

        @Override
        public boolean isOptional() {
            return optional;
        }

        @Override
        public void setOptional(boolean optional) {
            this.optional = optional;
        }

        @Override
        public void setValidationAction(ValidationAction action) {
            this.validationAction = action;
        }

        @Override
        public void setConfigureAction(UpdateAction action) {
            this.configureAction = action;
        }

        public Class<?> getNestedType() {
            return nestedType;
        }

        @Override
        public void setNestedType(Class<?> nestedType) {
            this.nestedType = nestedType;
        }

        public TaskPropertyInfo createProperty() {
            if (configureAction == null && validationAction == null) {
                return null;
            }
            return new TaskPropertyInfo(parent, name, propertyType, method, validationAction, configureAction, optional);
        }
    }
}
