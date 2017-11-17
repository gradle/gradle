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
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import groovy.lang.GroovyObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.options.OptionValues;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.Types;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class DefaultTaskClassValidatorExtractor implements TaskClassValidatorExtractor {
    // Avoid reflecting on classes we know we don't need to look at
    @SuppressWarnings("RedundantTypeArguments")
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
        new DestroysPropertyAnnotationHandler(),
        new LocalStatePropertyAnnotationHandler(),
        new NestedBeanPropertyAnnotationHandler(),
        new NoOpPropertyAnnotationHandler(Inject.class),
        new NoOpPropertyAnnotationHandler(Console.class),
        new NoOpPropertyAnnotationHandler(Internal.class),
        new NoOpPropertyAnnotationHandler(OptionValues.class)
    );

    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> annotationHandlers;
    private final Multimap<Class<? extends Annotation>, Class<? extends Annotation>> annotationOverrides;
    private final Set<Class<? extends Annotation>> relevantAnnotationTypes;

    public DefaultTaskClassValidatorExtractor(PropertyAnnotationHandler... customAnnotationHandlers) {
        this(Arrays.asList(customAnnotationHandlers));
    }

    public DefaultTaskClassValidatorExtractor(Iterable<? extends PropertyAnnotationHandler> customAnnotationHandlers) {
        Iterable<PropertyAnnotationHandler> allAnnotationHandlers = Iterables.concat(HANDLERS, customAnnotationHandlers);
        Map<Class<? extends Annotation>, PropertyAnnotationHandler> annotationsHandlers = Maps.uniqueIndex(allAnnotationHandlers, new Function<PropertyAnnotationHandler, Class<? extends Annotation>>() {
            @Override
            public Class<? extends Annotation> apply(PropertyAnnotationHandler handler) {
                return handler.getAnnotationType();
            }
        });
        this.annotationHandlers = annotationsHandlers;
        this.annotationOverrides = collectAnnotationOverrides(allAnnotationHandlers);
        this.relevantAnnotationTypes = collectRelevantAnnotationTypes(annotationsHandlers.keySet());
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

    private static Set<Class<? extends Annotation>> collectRelevantAnnotationTypes(Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        return ImmutableSet.<Class<? extends Annotation>>builder()
            .addAll(propertyTypeAnnotations)
            .add(Optional.class)
            .add(SkipWhenEmpty.class)
            .add(PathSensitive.class)
            .build();
    }

    @Override
    public TaskClassValidator extractValidator(Class<? extends Task> type) {
        boolean cacheable = type.isAnnotationPresent(CacheableTask.class);
        ImmutableSortedSet.Builder<TaskPropertyInfo> annotatedPropertiesBuilder = ImmutableSortedSet.naturalOrder();
        ImmutableList.Builder<TaskClassValidationMessage> validationMessages = ImmutableList.builder();
        Queue<TypeEntry> queue = new ArrayDeque<TypeEntry>();
        queue.add(new TypeEntry(null, type));
        while (!queue.isEmpty()) {
            TypeEntry entry = queue.remove();
            parseProperties(entry.parent, entry.type, annotatedPropertiesBuilder, validationMessages, cacheable, queue);
        }
        return new TaskClassValidator(annotatedPropertiesBuilder.build(), validationMessages.build(), cacheable);
    }

    private <T> void parseProperties(final TaskPropertyInfo parent, Class<T> type, ImmutableSet.Builder<TaskPropertyInfo> annotatedProperties, final ImmutableCollection.Builder<TaskClassValidationMessage> validationMessages, final boolean cacheable, Queue<TypeEntry> queue) {
        final Set<Class<? extends Annotation>> propertyTypeAnnotations = annotationHandlers.keySet();
        final Map<String, DefaultTaskPropertyActionContext> propertyContexts = Maps.newLinkedHashMap();
        Types.walkTypeHierarchy(type, IGNORED_SUPER_CLASSES, new Types.TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                Map<String, Field> fields = getFields(type);
                List<Getter> getters = getGetters(type);
                for (Getter getter : getters) {
                    Method method = getter.getMethod();
                    String fieldName = getter.getName();
                    Field field = fields.get(fieldName);
                    String propertyName = parent != null ? parent.getName() + '.' + fieldName : fieldName;

                    DefaultTaskPropertyActionContext propertyContext = propertyContexts.get(propertyName);
                    if (propertyContext == null) {
                        propertyContext = new DefaultTaskPropertyActionContext(propertyTypeAnnotations, parent, propertyName, method, cacheable, validationMessages);
                        propertyContexts.put(propertyName, propertyContext);
                    }

                    if (field != null) {
                        propertyContext.setInstanceVariableField(field);
                    }
                    Iterable<Annotation> declaredAnnotations = mergeDeclaredAnnotations(propertyContext, method, field);

                    // Discard overridden property type annotations when an overriding annotation is also present
                    Iterable<Annotation> overriddenAnnotations = filterOverridingAnnotations(declaredAnnotations, propertyTypeAnnotations);

                    recordAnnotations(propertyContext, overriddenAnnotations, propertyTypeAnnotations);
                }
            }
        });
        for (DefaultTaskPropertyActionContext propertyContext : propertyContexts.values()) {
            TaskPropertyInfo property = createProperty(propertyContext);
            if (property != null) {
                annotatedProperties.add(property);
                Class<?> nestedType = propertyContext.getNestedType();
                if (nestedType != null) {
                    queue.add(new TypeEntry(property, nestedType));
                }
            }
        }
    }

    private Iterable<Annotation> mergeDeclaredAnnotations(TaskPropertyActionContext propertyContext, Method method, Field field) {
        Collection<Annotation> methodAnnotations = collectRelevantAnnotations(method.getDeclaredAnnotations());
        if (Modifier.isPrivate(method.getModifiers()) && !methodAnnotations.isEmpty()) {
            propertyContext.validationMessage("is private and annotated with an input or output annotation");
        }
        if (field == null) {
            return methodAnnotations;
        }
        Collection<Annotation> fieldAnnotations = collectRelevantAnnotations(field.getDeclaredAnnotations());
        if (fieldAnnotations.isEmpty()) {
            return methodAnnotations;
        }
        if (methodAnnotations.isEmpty()) {
            return fieldAnnotations;
        }

        for (Annotation methodAnnotation : methodAnnotations) {
            Iterator<Annotation> iFieldAnnotation = fieldAnnotations.iterator();
            while (iFieldAnnotation.hasNext()) {
                Annotation fieldAnnotation = iFieldAnnotation.next();
                if (methodAnnotation.annotationType().equals(fieldAnnotation.annotationType())) {
                    propertyContext.validationMessage("has both a getter and field declared with annotation @" + methodAnnotation.annotationType().getSimpleName());
                    iFieldAnnotation.remove();
                }
            }
        }

        return Iterables.concat(methodAnnotations, fieldAnnotations);
    }

    private Collection<Annotation> collectRelevantAnnotations(Annotation[] annotations) {
        List<Annotation> relevantAnnotations = Lists.newArrayListWithCapacity(annotations.length);
        for (Annotation annotation : annotations) {
            if (relevantAnnotationTypes.contains(annotation.annotationType())) {
                relevantAnnotations.add(annotation);
            }
        }
        return relevantAnnotations;
    }

    private Iterable<Annotation> filterOverridingAnnotations(final Iterable<Annotation> declaredAnnotations, final Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        return Iterables.filter(declaredAnnotations, new Predicate<Annotation>() {
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
        });
    }

    private void recordAnnotations(TaskPropertyActionContext propertyContext, Iterable<Annotation> annotations, Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        Set<Class<? extends Annotation>> declaredPropertyTypes = Sets.newLinkedHashSet();
        for (Annotation annotation : annotations) {
            if (propertyTypeAnnotations.contains(annotation.annotationType())) {
                declaredPropertyTypes.add(annotation.annotationType());
            }
            propertyContext.addAnnotation(annotation);
        }

        if (declaredPropertyTypes.size() > 1) {
            propertyContext.validationMessage("has conflicting property types declared: "
                    + Joiner.on(", ").join(Iterables.transform(declaredPropertyTypes, new Function<Class<? extends Annotation>, String>() {
                    @Override
                    public String apply(Class<? extends Annotation> annotationType) {
                        return "@" + annotationType.getSimpleName();
                    }
                }))
            );
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

    private TaskPropertyInfo createProperty(DefaultTaskPropertyActionContext propertyContext) {
        Class<? extends Annotation> propertyType = propertyContext.getPropertyType();
        if (propertyType != null) {
            if (propertyContext.isAnnotationPresent(Optional.class)) {
                propertyContext.setOptional(true);
            }

            PropertyAnnotationHandler handler = annotationHandlers.get(propertyType);
            handler.attachActions(propertyContext);

            return propertyContext.createProperty();
        } else {
            if (!Modifier.isPrivate(propertyContext.method.getModifiers())) {
                propertyContext.validationMessage("is not annotated with an input or output annotation");
            }
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

    private static List<Getter> getGetters(Class<?> type) {
        Method[] methods = type.getDeclaredMethods();
        List<Getter> getters = Lists.newArrayListWithCapacity(methods.length);
        for (Method method : methods) {
            PropertyAccessorType accessorType = PropertyAccessorType.of(method);
            // We only care about getters
            if (accessorType == null || accessorType == PropertyAccessorType.SETTER) {
                continue;
            }
            // We only care about actual methods the user added
            if (method.isBridge() || GroovyMethods.isObjectMethod(method)) {
                continue;
            }
            getters.add(new DefaultTaskClassValidatorExtractor.Getter(method, accessorType.propertyNameFor(method)));
        }
        Collections.sort(getters);
        return getters;
    }

    private static class Getter implements Comparable<Getter> {
        private final Method method;
        private final String name;

        public Getter(Method method, String name) {
            this.method = method;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Method getMethod() {
            return method;
        }

        @Override
        public int compareTo(@Nonnull Getter o) {
            // Sort "is"-getters before "get"-getters when both are available
            return method.getName().compareTo(o.method.getName());
        }
    }

    private static class DefaultTaskPropertyActionContext implements TaskPropertyActionContext {
        private final Set<Class<? extends Annotation>> propertyTypeAnnotations;
        private final TaskPropertyInfo parent;
        private final String name;
        private final Method method;
        private final List<Annotation> annotations = Lists.newArrayList();
        private final boolean cacheable;
        private final ImmutableCollection.Builder<TaskClassValidationMessage> validationMessages;
        private Field instanceVariableField;
        private UpdateAction configureAction;
        private boolean optional;
        private Class<?> nestedType;
        private Class<? extends Annotation> propertyType;

        public DefaultTaskPropertyActionContext(Set<Class<? extends Annotation>> propertyTypeAnnotations, TaskPropertyInfo parent, String name, Method method, boolean cacheable, ImmutableCollection.Builder<TaskClassValidationMessage> validationMessages) {
            this.propertyTypeAnnotations = propertyTypeAnnotations;
            this.parent = parent;
            this.name = name;
            this.method = method;
            this.cacheable = cacheable;
            this.validationMessages = validationMessages;
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
        public void addAnnotation(Annotation annotation) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            // Record the most specific property type annotation only
            if (propertyType == null && isPropertyTypeAnnotation(annotationType)) {
                propertyType = annotationType;
            }
            // Record the most specific annotation only
            if (!isAnnotationPresent(annotation.annotationType())) {
                annotations.add(annotation);
            }
        }

        private boolean isPropertyTypeAnnotation(Class<? extends Annotation> annotationType) {
            return propertyTypeAnnotations.contains(annotationType);
        }

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
        public boolean isCacheable() {
            return cacheable;
        }

        @Override
        public void setOptional(boolean optional) {
            this.optional = optional;
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
            if (configureAction == null) {
                return null;
            }
            return new TaskPropertyInfo(parent, name, propertyType, method, configureAction);
        }

        @Override
        public void validationMessage(String message) {
            validationMessages.add(TaskClassValidationMessage.property(name, message));
        }
    }
}
