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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
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
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.tasks.options.OptionValues;
import org.gradle.api.internal.tasks.properties.annotations.DestroysPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputFilePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.LocalStatePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.NestedBeanPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.NoOpPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoriesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputFilePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OverridingPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.Types;
import org.gradle.internal.scripts.ScriptOrigin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultPropertyMetadataStore implements PropertyMetadataStore {
    // Avoid reflecting on classes we know we don't need to look at
    @SuppressWarnings("RedundantTypeArguments")
    private static final Collection<Class<?>> IGNORED_SUPER_CLASSES = ImmutableSet.<Class<?>>of(
        ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class, IConventionAware.class, ExtensionAware.class, HasConvention.class, ScriptOrigin.class, DynamicObjectAware.class
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
    private final LoadingCache<Class<?>, Set<PropertyMetadata>> cache = CacheBuilder.newBuilder()
        .weakKeys()
        .build(new CacheLoader<Class<?>, Set<PropertyMetadata>>() {
            @Override
            public Set<PropertyMetadata> load(@Nonnull Class<?> type) throws Exception {
                return createTypeMetadata(type);
            }
        });

    public DefaultPropertyMetadataStore(Iterable<? extends PropertyAnnotationHandler> customAnnotationHandlers) {
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
    public <T> Set<PropertyMetadata> getTypeMetadata(Class<T> type) {
        return cache.getUnchecked(type);
    }

    private <T> Set<PropertyMetadata> createTypeMetadata(Class<T> type) {
        final Set<Class<? extends Annotation>> propertyTypeAnnotations = annotationHandlers.keySet();
        final Map<String, DefaultPropertyMetadata> propertyContexts = Maps.newLinkedHashMap();
        Types.walkTypeHierarchy(type, IGNORED_SUPER_CLASSES, new Types.TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                if (type.isSynthetic()) {
                    return;
                }
                Map<String, Field> fields = getFields(type);
                List<Getter> getters = getGetters(type);
                for (Getter getter : getters) {
                    Method method = getter.getMethod();
                    if (method.isSynthetic()) {
                        continue;
                    }
                    if (method.getName().equals("getContentHash") || method.getName().equals("getOriginalClassName")) {
                        continue;
                    }
                    String fieldName = getter.getName();
                    Field field = fields.get(fieldName);
                    if (field != null && field.isSynthetic()) {
                        continue;
                    }

                    DefaultPropertyMetadata propertyMetadata = propertyContexts.get(fieldName);
                    if (propertyMetadata == null) {
                        propertyMetadata = new DefaultPropertyMetadata(propertyTypeAnnotations, fieldName, method);
                        propertyContexts.put(fieldName, propertyMetadata);
                    }

                    Iterable<Annotation> declaredAnnotations = mergeDeclaredAnnotations(method, field, propertyMetadata);

                    // Discard overridden property type annotations when an overriding annotation is also present
                    Iterable<Annotation> overriddenAnnotations = filterOverridingAnnotations(declaredAnnotations, propertyTypeAnnotations);

                    recordAnnotations(propertyMetadata, overriddenAnnotations, propertyTypeAnnotations);
                }
            }
        });
        return ImmutableSet.<PropertyMetadata>builder().addAll(propertyContexts.values()).build();
    }

    private Iterable<Annotation> mergeDeclaredAnnotations(Method method, @Nullable Field field, DefaultPropertyMetadata propertyContext) {
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

    private void recordAnnotations(DefaultPropertyMetadata propertyContext, Iterable<Annotation> annotations, Set<Class<? extends Annotation>> propertyTypeAnnotations) {
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


    private Collection<Annotation> collectRelevantAnnotations(Annotation[] annotations) {
        List<Annotation> relevantAnnotations = Lists.newArrayListWithCapacity(annotations.length);
        for (Annotation annotation : annotations) {
            if (relevantAnnotationTypes.contains(annotation.annotationType())) {
                relevantAnnotations.add(annotation);
            }
        }
        return relevantAnnotations;
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

    private class DefaultPropertyMetadata implements PropertyMetadata {
        private final Set<Class<? extends Annotation>> propertyTypeAnnotations;
        private final String fieldName;
        private final Method method;
        private Class<? extends Annotation> propertyType;
        private final List<Annotation> annotations = Lists.newArrayList();
        private final List<String> validationMessages = Lists.newArrayList();

        public DefaultPropertyMetadata(Set<Class<? extends Annotation>> propertyTypeAnnotations, String fieldName, Method method) {
            this.propertyTypeAnnotations = propertyTypeAnnotations;
            this.fieldName = fieldName;
            this.method = method;
        }

        @Override
        public String getFieldName() {
            return fieldName;
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

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return getAnnotation(annotationType) != null;
        }

        @Override
        @Nullable
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            for (Annotation annotation : annotations) {
                if (annotationType.equals(annotation.annotationType())) {
                    return annotationType.cast(annotation);
                }
            }
            return null;
        }

        public void validationMessage(String message) {
            validationMessages.add(message);
        }

        @Override
        public List<String> getValidationMessages() {
            return validationMessages;
        }

        @Override
        public List<Annotation> getAnnotations() {
            return annotations;
        }

        @Override
        @Nullable
        public PropertyAnnotationHandler getPropertyValueVisitor() {
            return annotationHandlers.get(propertyType);
        }

        @Override
        @Nullable
        public Class<? extends Annotation> getPropertyType() {
            return propertyType;
        }

        @Override
        public Class<?> getDeclaredType() {
            return method.getReturnType();
        }

        @Override
        public Method getMethod() {
            return method;
        }
    }

}
