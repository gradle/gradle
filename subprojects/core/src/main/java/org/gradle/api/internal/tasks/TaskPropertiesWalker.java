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

package org.gradle.api.internal.tasks;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import groovy.lang.GroovyObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.project.taskfactory.DestroysPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.InputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.InputFilePropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.InputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.InputPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.LocalStatePropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.NestedBeanPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.NoOpPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.OutputDirectoriesPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.OutputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.OutputFilePropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.OutputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.OverridingPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.PropertyAnnotationHandler;
import org.gradle.api.internal.tasks.options.OptionValues;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.Types;
import org.gradle.util.DeferredUtil;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.gradle.api.internal.tasks.TaskValidationContext.Severity.ERROR;

@NonNullApi
public class TaskPropertiesWalker {

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

    public TaskPropertiesWalker(Iterable<? extends PropertyAnnotationHandler> customAnnotationHandlers) {
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

    public void visitInputs(PropertySpecFactory specFactory, InputsOutputVisitor visitor, Object instance) {
        Queue<PropertyContainer> queue = new ArrayDeque<PropertyContainer>();
        queue.add(new PropertyContainer(null, instance));
        while (!queue.isEmpty()) {
            PropertyContainer container = queue.remove();
            detectProperties(container, container.getInstance().getClass(), queue, visitor, specFactory);
        }
    }

    private <T> void detectProperties(PropertyContainer container, Class<T> type, Queue<PropertyContainer> queue, InputsOutputVisitor visitor, PropertySpecFactory inputs) {
        final Set<Class<? extends Annotation>> propertyTypeAnnotations = annotationHandlers.keySet();
        final Map<String, DefaultPropertyContext> propertyContexts = Maps.newLinkedHashMap();
        Types.walkTypeHierarchy(type, IGNORED_SUPER_CLASSES, new Types.TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                Map<String, Field> fields = getFields(type);
                List<Getter> getters = getGetters(type);
                for (Getter getter : getters) {
                    Method method = getter.getMethod();
                    String fieldName = getter.getName();
                    Field field = fields.get(fieldName);

                    DefaultPropertyContext propertyContext = propertyContexts.get(fieldName);
                    if (propertyContext == null) {
                        propertyContext = new DefaultPropertyContext(propertyTypeAnnotations, fieldName, method);
                        propertyContexts.put(fieldName, propertyContext);
                    }

                    Iterable<Annotation> declaredAnnotations = mergeDeclaredAnnotations(method, field);

                    // Discard overridden property type annotations when an overriding annotation is also present
                    Iterable<Annotation> overriddenAnnotations = filterOverridingAnnotations(declaredAnnotations, propertyTypeAnnotations);

                    recordAnnotations(propertyContext, overriddenAnnotations, propertyTypeAnnotations);
                }
            }
        });
        for (DefaultPropertyContext propertyContext : propertyContexts.values()) {
            Class<? extends Annotation> propertyType = propertyContext.propertyType;
            if (propertyType != null) {
                PropertyAnnotationHandler annotationHandler = annotationHandlers.get(propertyType);
                Object instance = container.getInstance();
                String propertyName = container.getRelativePropertyName(propertyContext.fieldName);
                PropertyInfo propertyInfo = propertyContext.createPropertyInfo(propertyName, instance);
                annotationHandler.accept(propertyInfo, visitor, inputs);
                if (propertyInfo.isAnnotationPresent(Nested.class)) {
                    try {
                        Object nestedBean = propertyInfo.getValue();
                        if (nestedBean != null) {
                            queue.add(new PropertyContainer(propertyName, nestedBean));
                        }
                    } catch (Exception e) {
                        // No nested bean
                    }
                }
            }
        }
    }
    private Iterable<Annotation> mergeDeclaredAnnotations(Method method, @Nullable Field field) {
        Collection<Annotation> methodAnnotations = collectRelevantAnnotations(method.getDeclaredAnnotations());
//        if (Modifier.isPrivate(method.getModifiers()) && !methodAnnotations.isEmpty()) {
// FIXME           propertyContext.validationMessage("is private and annotated with an input or output annotation");
//        }
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
// FIXME                   propertyContext.validationMessage("has both a getter and field declared with annotation @" + methodAnnotation.annotationType().getSimpleName());
                    iFieldAnnotation.remove();
                }
            }
        }

        return Iterables.concat(methodAnnotations, fieldAnnotations);
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

    private void recordAnnotations(DefaultPropertyContext propertyContext, Iterable<Annotation> annotations, Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        Set<Class<? extends Annotation>> declaredPropertyTypes = Sets.newLinkedHashSet();
        for (Annotation annotation : annotations) {
            if (propertyTypeAnnotations.contains(annotation.annotationType())) {
                declaredPropertyTypes.add(annotation.annotationType());
            }
            propertyContext.addAnnotation(annotation);
        }

//        if (declaredPropertyTypes.size() > 1) {
// FIXME           propertyContext.validationMessage("has conflicting property types declared: "
//                    + Joiner.on(", ").join(Iterables.transform(declaredPropertyTypes, new Function<Class<? extends Annotation>, String>() {
//                    @Override
//                    public String apply(Class<? extends Annotation> annotationType) {
//                        return "@" + annotationType.getSimpleName();
//                    }
//                }))
//            );
//        }
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

    private class PropertyContainer {
        @Nullable
        private final String parentPropertyName;
        private final Object object;

        public PropertyContainer(@Nullable String parentPropertyName, Object object) {
            this.parentPropertyName = parentPropertyName;
            this.object = object;
        }

        public Object getInstance() {
            return object;
        }

        public String getRelativePropertyName(String propertyName) {
            return parentPropertyName == null ? propertyName : parentPropertyName + "." + propertyName;
        }
    }

    private class DefaultPropertyContext {
        private final Set<Class<? extends Annotation>> propertyTypeAnnotations;
        private final String fieldName;
        private final Method method;
        private Class<? extends Annotation> propertyType;
        private final List<Annotation> annotations = Lists.newArrayList();

        public DefaultPropertyContext(Set<Class<? extends Annotation>> propertyTypeAnnotations, String fieldName, Method method) {
            this.propertyTypeAnnotations = propertyTypeAnnotations;
            this.fieldName = fieldName;
            this.method = method;
        }

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

        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return getAnnotation(annotationType) != null;
        }

        @Nullable
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            for (Annotation annotation : annotations) {
                if (annotationType.equals(annotation.annotationType())) {
                    return annotationType.cast(annotation);
                }
            }
            return null;
        }

        public PropertyInfo createPropertyInfo(String propertyName, Object instance) {
            return new DefaultPropertyInfo(propertyName, annotations, instance, method);
        }

    }

    private static class DefaultPropertyInfo implements PropertyInfo {
        private final String propertyName;
        private final List<Annotation> annotations;
        private final Object instance;
        private final Method method;

        public DefaultPropertyInfo(String propertyName, List<Annotation> annotations, Object instance, Method method) {
            this.propertyName = propertyName;
            this.annotations = ImmutableList.copyOf(annotations);
            this.instance = instance;
            this.method = method;
        }

        @Override
        public String getName() {
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
            Object value = DeprecationLogger.whileDisabled(new Factory<Object>() {
                public Object create() {
                    try {
                        return method.invoke(instance);
                    } catch (InvocationTargetException e) {
                        throw UncheckedException.throwAsUncheckedException(e.getCause());
                    } catch (Exception e) {
                        throw new GradleException(String.format("Could not call %s.%s() on %s", method.getDeclaringClass().getSimpleName(), method.getName(), instance), e);
                    }
                }
            });
            return value instanceof Provider ? ((Provider<?>) value).getOrNull() : value;
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
            getters.add(new Getter(method, accessorType.propertyNameFor(method)));
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

}
