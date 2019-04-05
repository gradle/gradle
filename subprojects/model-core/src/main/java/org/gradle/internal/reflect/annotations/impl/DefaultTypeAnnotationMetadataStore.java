/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.reflect.annotations.impl;

import com.google.common.base.Equivalence;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.annotations.PropertyAnnotationCategory;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.internal.reflect.Methods.SIGNATURE_EQUIVALENCE;

public class DefaultTypeAnnotationMetadataStore implements TypeAnnotationMetadataStore {
    private static final TypeAnnotationMetadata EMPTY_TYPE_ANNOTATION_METADATA = new TypeAnnotationMetadata() {
        @Override
        public ImmutableSet<Annotation> getAnnotations() {
            return ImmutableSet.of();
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return false;
        }

        @Override
        public ImmutableSortedSet<PropertyAnnotationMetadata> getPropertiesAnnotationMetadata() {
            return ImmutableSortedSet.of();
        }

        @Override
        public void visitValidationFailures(@Nullable String ownerPropertyPath, ParameterValidationContext validationContext) {
        }
    };

    private final ImmutableMap<Class<? extends Annotation>, PropertyAnnotationCategory> propertyAnnotationCategories;
    private final Set<Class<? extends Annotation>> recordedTypeAnnotations;
    private final CrossBuildInMemoryCache<Class<?>, TypeAnnotationMetadata> cache;
    private final Set<Equivalence.Wrapper<Method>> ignoredMethods;

    public DefaultTypeAnnotationMetadataStore(
        Collection<Class<? extends Annotation>> recordedTypeAnnotations,
        Map<Class<? extends Annotation>, ? extends PropertyAnnotationCategory> propertyAnnotationCategories,
        Collection<Class<?>> ignoredSuperClasses,
        Collection<Class<?>> ignoreMethodsFromClasses,
        CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        this.recordedTypeAnnotations = ImmutableSet.copyOf(recordedTypeAnnotations);
        this.propertyAnnotationCategories = ImmutableMap.copyOf(propertyAnnotationCategories);
        this.cache = cacheFactory.newClassCache();
        for (Class<?> ignoredSuperClass : ignoredSuperClasses) {
            cache.put(ignoredSuperClass, EMPTY_TYPE_ANNOTATION_METADATA);
        }
        this.ignoredMethods = allMethodsOf(ignoreMethodsFromClasses);
    }

    private static ImmutableSet<Equivalence.Wrapper<Method>> allMethodsOf(Iterable<Class<?>> classes) {
        List<Equivalence.Wrapper<Method>> methods = Lists.newArrayList();
        for (Class<?> clazz : classes) {
            for (Method method : clazz.getMethods()) {
                methods.add(SIGNATURE_EQUIVALENCE.wrap(method));
            }
        }
        return ImmutableSet.copyOf(methods);
    }

    @Override
    public TypeAnnotationMetadata getTypeAnnotationMetadata(Class<?> type) {
        return cache.get(type, t -> createTypeAnnotationMetadata(type));
    }

    private TypeAnnotationMetadata createTypeAnnotationMetadata(Class<?> type) {
        ImmutableSet.Builder<Annotation> typeAnnotations = ImmutableSet.builder();
        for (Annotation typeAnnotation : type.getDeclaredAnnotations()) {
            if (recordedTypeAnnotations.contains(typeAnnotation.annotationType())) {
                typeAnnotations.add(typeAnnotation);
            }
        }

        Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders = extractPropertiesFrom(type);
        Map<String, PropertyAnnotationMetadata> propertiesMetadata = new HashMap<>();
        visitSuperTypes(type, superType -> {
            for (PropertyAnnotationMetadata propertyAnnotationMetadata : superType.getPropertiesAnnotationMetadata()) {
                String propertyName = propertyAnnotationMetadata.getPropertyName();
                PropertyAnnotationMetadataBuilder builder = propertyBuilders.get(propertyName);
                if (builder == null) {
                    propertiesMetadata.putIfAbsent(propertyName, propertyAnnotationMetadata);
                } else {
                    builder.inherit(propertyAnnotationMetadata.getAnnotations());
                }
            }
        });
        propertyBuilders.forEach((propertyName, builder) -> {
            propertiesMetadata.put(propertyName, builder.build());
        });
        return new DefaultTypeAnnotationMetadata(typeAnnotations.build(), propertiesMetadata.values());
    }

    private void visitSuperTypes(Class<?> type, Consumer<? super TypeAnnotationMetadata> visitor) {
        Arrays.stream(type.getInterfaces())
            .map(iface -> getTypeAnnotationMetadata(iface))
            .forEach(visitor);
        Class<?> superclass = type.getSuperclass();
        if (superclass != null) {
            visitor.accept(getTypeAnnotationMetadata(superclass));
        }
    }

    private Map<String, PropertyAnnotationMetadataBuilder> extractPropertiesFrom(Class<?> type) {
        if (type.isSynthetic()) {
            return ImmutableMap.of();
        }

        Map<String, Field> fields = findFields(type);
        List<Getter> getters = findGetters(type);

        // TODO Warn when ignoring annotations on setters and other non-property methods
        Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders = new HashMap<>();
        for (Getter getter : getters) {
            Method method = getter.getMethod();
            if (method.isSynthetic()) {
                continue;
            }
            String propertyName = getter.getName();
            Field field = fields.get(propertyName);
            if (field != null && field.isSynthetic()) {
                continue;
            }

            PropertyAnnotationMetadataBuilder propertyBuilder = new PropertyAnnotationMetadataBuilder(propertyName, method);
            boolean privateGetter = Modifier.isPrivate(method.getModifiers());
            mergeDeclaredAnnotations(method, privateGetter, field, propertyBuilder).forEach(declaredAnnotation -> {
                propertyBuilder.recordAnnotation(declaredAnnotation);
            });
            // Ignore private getters without annotations
            if (privateGetter && !propertyBuilder.hasRecordedAnnotation()) {
                continue;
            }
            propertyBuilders.put(propertyName, propertyBuilder);
        }
        return propertyBuilders;
    }

    private Iterable<Annotation> mergeDeclaredAnnotations(Method getter, boolean privateGetter, @Nullable Field field, PropertyAnnotationMetadataBuilder builder) {
        Collection<Annotation> methodAnnotations = collectRelevantAnnotations(getter.getDeclaredAnnotations());
        if (privateGetter) {
            if (!methodAnnotations.isEmpty()) {
                builder.recordProblem(String.format("is private and annotated with @%s",
                    methodAnnotations.iterator().next().annotationType().getSimpleName()));
            }
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
                    builder.recordProblem(String.format("has both a getter and field declared with annotation @%s",
                        methodAnnotation.annotationType().getSimpleName()));
                    iFieldAnnotation.remove();
                }
            }
        }

        return Iterables.concat(methodAnnotations, fieldAnnotations);
    }

    private Collection<Annotation> collectRelevantAnnotations(Annotation[] annotations) {
        List<Annotation> relevantAnnotations = Lists.newArrayListWithCapacity(annotations.length);
        for (Annotation annotation : annotations) {
            if (propertyAnnotationCategories.keySet().contains(annotation.annotationType())) {
                relevantAnnotations.add(annotation);
            }
        }
        return relevantAnnotations;
    }

    private static Map<String, Field> findFields(Class<?> type) {
        Map<String, Field> fields = Maps.newHashMap();
        for (Field field : type.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
        return fields;
    }

    private List<Getter> findGetters(Class<?> type) {
        Method[] methods = type.getDeclaredMethods();
        List<Getter> getters = Lists.newArrayListWithCapacity(methods.length);
        for (Method method : methods) {
            PropertyAccessorType accessorType = PropertyAccessorType.of(method);
            // We only care about getters
            if (accessorType == null || accessorType == PropertyAccessorType.SETTER) {
                continue;
            }
            // We only care about actual methods the user added
            if (method.isBridge() || ignoredMethods.contains(SIGNATURE_EQUIVALENCE.wrap(method))) {
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

        Getter(Method method, String name) {
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

        @Override
        public String toString() {
            return method.getName();
        }
    }

    private class PropertyAnnotationMetadataBuilder {
        private final String propertyName;
        private final Method getter;
        private final ListMultimap<PropertyAnnotationCategory, Annotation> recordedAnnotations;
        private final ImmutableList.Builder<String> problems = ImmutableList.builder();

        public PropertyAnnotationMetadataBuilder(String propertyName, Method getter) {
            this.propertyName = propertyName;
            this.getter = getter;
            this.recordedAnnotations = ArrayListMultimap.create();
        }

        public void recordAnnotation(Annotation annotation) {
            PropertyAnnotationCategory category = propertyAnnotationCategories.get(annotation.annotationType());
            recordedAnnotations.put(category, annotation);
        }

        public boolean hasRecordedAnnotation() {
            return !recordedAnnotations.isEmpty();
        }

        public void recordProblem(String problem) {
            problems.add(problem);
        }

        public void inherit(Map<PropertyAnnotationCategory, Annotation> inheritedAnnotations) {
            inheritedAnnotations.forEach((category, inheritedAnnotation) -> {
                if (!recordedAnnotations.containsKey(category)) {
                    recordedAnnotations.put(category, inheritedAnnotation);
                }
            });
        }

        public PropertyAnnotationMetadata build() {
            ImmutableMap.Builder<PropertyAnnotationCategory, Annotation> annotations = ImmutableMap.builder();
            Multimaps.asMap(recordedAnnotations).forEach((category, recordedAnnotations) -> {
                // Ignore all but the first recorded annotation
                Annotation recordedAnnotation = recordedAnnotations.get(0);
                if (recordedAnnotations.size() > 1) {
                    recordProblem(String.format("has conflicting %s annotations declared: %s; assuming @%s",
                        category.getDisplayName(),
                        recordedAnnotations.stream()
                            .map(annotation -> "@" + annotation.annotationType().getSimpleName())
                            .collect(Collectors.joining(", ")),
                        recordedAnnotation.annotationType().getSimpleName()
                    ));
                }
                annotations.put(category, recordedAnnotation);
            });
            return new DefaultPropertyAnnotationMetadata(propertyName, getter, annotations.build(), problems.build());
        }
    }
}
