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
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        public void visitValidationFailures(@Nullable String ownerPath, ParameterValidationContext validationContext) {
        }
    };

    private final ImmutableMap<Class<? extends Annotation>, AnnotationCategory> annotationCategories;
    private final Set<Class<? extends Annotation>> recordedTypeAnnotations;
    private final CrossBuildInMemoryCache<Class<?>, TypeAnnotationMetadata> cache;
    private final Set<String> potentiallyIgnoredMethodNames;
    private final Set<Equivalence.Wrapper<Method>> globallyIgnoredMethods;
    private final Class<? extends Annotation> ignoredMethodAnnotation;

    public DefaultTypeAnnotationMetadataStore(
        Collection<Class<? extends Annotation>> recordedTypeAnnotations,
        Map<Class<? extends Annotation>, ? extends AnnotationCategory> annotationCategories,
        Collection<Class<?>> ignoredSuperClasses,
        Collection<Class<?>> ignoreMethodsFromClasses,
        Class<? extends Annotation> ignoreMethodAnnotation,
        CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        this.recordedTypeAnnotations = ImmutableSet.copyOf(recordedTypeAnnotations);
        this.annotationCategories = ImmutableMap.copyOf(annotationCategories);
        this.cache = cacheFactory.newClassCache();
        for (Class<?> ignoredSuperClass : ignoredSuperClasses) {
            cache.put(ignoredSuperClass, EMPTY_TYPE_ANNOTATION_METADATA);
        }
        this.ignoredMethodAnnotation = ignoreMethodAnnotation;
        this.potentiallyIgnoredMethodNames = allMethodsNamesOf(ignoreMethodsFromClasses);
        this.globallyIgnoredMethods = allMethodsOf(ignoreMethodsFromClasses);
    }

    private ImmutableSet<String> allMethodsNamesOf(Collection<Class<?>> classes) {
        ImmutableSet.Builder<String> methods = ImmutableSet.builder();
        for (Class<?> clazz : classes) {
            for (Method method : clazz.getMethods()) {
                methods.add(method.getName());
            }
        }
        return methods.build();
    }

    private static ImmutableSet<Equivalence.Wrapper<Method>> allMethodsOf(Iterable<Class<?>> classes) {
        ImmutableSet.Builder<Equivalence.Wrapper<Method>> methods = ImmutableSet.builder();
        for (Class<?> clazz : classes) {
            for (Method method : clazz.getMethods()) {
                methods.add(SIGNATURE_EQUIVALENCE.wrap(method));
            }
        }
        return methods.build();
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

        ImmutableList.Builder<String> problems = ImmutableList.builder();
        HashMap<String, PropertyAnnotationMetadataBuilder> propertyBuilders = new HashMap<>();

        ImmutableSet<String> methodsNotToInherit;
        if (type.isSynthetic()) {
            methodsNotToInherit = ImmutableSet.of();
        } else {
            ImmutableSet.Builder<String> locallyIgnoredMethods = ImmutableSet.builder();
            extractPropertiesFrom(type, propertyBuilders, locallyIgnoredMethods, problems);
            methodsNotToInherit = locallyIgnoredMethods.build();
        }

        Collection<PropertyAnnotationMetadata> propertiesMetadata = inheritProperties(type, propertyBuilders, methodsNotToInherit);

        return new DefaultTypeAnnotationMetadata(typeAnnotations.build(), propertiesMetadata, problems.build());
    }

    private Collection<PropertyAnnotationMetadata> inheritProperties(Class<?> type, HashMap<String, PropertyAnnotationMetadataBuilder> propertyBuilders, ImmutableSet<String> methodsNotToInherit) {
        Map<String, PropertyAnnotationMetadata> propertiesMetadata = new HashMap<>();
        visitSuperTypes(type, superType -> {
            for (PropertyAnnotationMetadata propertyAnnotationMetadata : superType.getPropertiesAnnotationMetadata()) {
                if (methodsNotToInherit.contains(propertyAnnotationMetadata.getMethod().getName())) {
                    continue;
                }
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
        return propertiesMetadata.values();
    }

    private void extractPropertiesFrom(Class<?> type, Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders, ImmutableSet.Builder<String> locallyIgnoredMethods, ImmutableList.Builder<String> validationErrors) {
        Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, (a, b) -> a.getName().compareTo(b.getName()));
        for (Method method : methods) {
            processMethodAnnotations(method, propertyBuilders, locallyIgnoredMethods, validationErrors);
        }

        for (Field field : type.getDeclaredFields()) {
            processFieldAnnotations(field, propertyBuilders, validationErrors);
        }
    }

    private void processMethodAnnotations(Method method, Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders, ImmutableSet.Builder<String> locallyIgnoredMethods, ImmutableList.Builder<String> validationErrors) {
        if (method.isSynthetic()) {
            return;
        }
        if (method.isBridge()) {
            return;
        }
        // As an optimization first check if the method name is among the candidates before we construct an equivalence wrapper
        if (potentiallyIgnoredMethodNames.contains(method.getName())
            && globallyIgnoredMethods.contains(SIGNATURE_EQUIVALENCE.wrap(method))) {
            return;
        }

        Map<Class<? extends Annotation>, Annotation> annotations = collectRelevantAnnotations(method.getDeclaredAnnotations());

        if (Modifier.isStatic(method.getModifiers())) {
            // TODO Add a test for this
            validateNotAnnotated("static", method, annotations.keySet(), validationErrors);
            return;
        }

        PropertyAccessorType accessorType = PropertyAccessorType.of(method);
        if (accessorType == null) {
            // TODO Add a test for this
            validateNotAnnotated("non-property", method, annotations.keySet(), validationErrors);
            return;
        } else if (accessorType == PropertyAccessorType.SETTER) {
            // TODO Add a test for this
            validateNotAnnotated("setter", method, annotations.keySet(), validationErrors);
            return;
        }

        // After this point we only see getters

        // Ignore private getters without annotations
        boolean privateGetter = Modifier.isPrivate(method.getModifiers());
        if (privateGetter && annotations.isEmpty()) {
            return;
        }

        if (annotations.containsKey(ignoredMethodAnnotation)) {
            if (annotations.size() != 1) {
                // TODO Add a test for this
                validationErrors.add(String.format("getter '%s()' annotated with @%s should not be also annotated with %s",
                    method.getName(),
                    ignoredMethodAnnotation.getSimpleName(),
                    simpleAnnotationNames(annotations.keySet().stream()
                        .filter(annotationType -> !annotationType.equals(ignoredMethodAnnotation)))
                ));
            }
            locallyIgnoredMethods.add(method.getName());
            return;
        }

        String propertyName = accessorType.propertyNameFor(method);
        PropertyAnnotationMetadataBuilder metadataBuilder = new PropertyAnnotationMetadataBuilder(propertyName, method);
        PropertyAnnotationMetadataBuilder previouslySeenBuilder = propertyBuilders.putIfAbsent(propertyName, metadataBuilder);
        if (previouslySeenBuilder != null) {
            // TODO Add a test for this
            previouslySeenBuilder.recordProblem(String.format("has redundant getters: '%s()' and '%s()'",
                method.getName(),
                previouslySeenBuilder.getter.getName()));
            return;
        }

        if (privateGetter) {
            // At this point we must have annotations on this private getter
            metadataBuilder.recordProblem(String.format("is private and annotated with %s",
                simpleAnnotationNames(annotations.keySet().stream())));
        }

        for (Annotation annotation : annotations.values()) {
            metadataBuilder.recordAnnotation(annotation);
        }
    }

    private void processFieldAnnotations(Field field, Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders, ImmutableList.Builder<String> validationErrors) {
        if (field.isSynthetic()) {
            return;
        }

        ImmutableMap<Class<? extends Annotation>, Annotation> annotations = collectRelevantAnnotations(field.getDeclaredAnnotations());
        if (annotations.isEmpty()) {
            return;
        }

        if (annotations.containsKey(ignoredMethodAnnotation)) {
            // TODO Add a test for this
            validationErrors.add("field '%s' should not be annotated with @%s",
                ignoredMethodAnnotation.getSimpleName());
        }

        PropertyAnnotationMetadataBuilder metadataBuilder = propertyBuilders.get(field.getName());
        if (metadataBuilder == null) {
            // TODO Add a test for this
            validationErrors.add("field '%s' without corresponding getter has been annotated with @%s",
                field.getName(),
                simpleAnnotationNames(annotations.keySet().stream()));
            return;
        }

        for (Annotation annotation : annotations.values()) {
            if (!annotation.annotationType().equals(ignoredMethodAnnotation)) {
                metadataBuilder.recordAnnotation(annotation);
            }
        }
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

    private static void validateNotAnnotated(String methodKind, Method method, Set<Class<? extends Annotation>> annotationTypes, ImmutableList.Builder<String> validationErrors) {
        if (!annotationTypes.isEmpty()) {
            validationErrors.add(String.format("%s method '%s()' should not be annotated with: %s",
                methodKind, method.getName(), simpleAnnotationNames(annotationTypes.stream())
            ));
        }
    }

    private static String simpleAnnotationNames(Stream<Class<? extends Annotation>> annotationTypes) {
        return annotationTypes
            .map(annotationType -> "@" + annotationType.getSimpleName())
            .collect(Collectors.joining(", "));
    }

    private ImmutableMap<Class<? extends Annotation>, Annotation> collectRelevantAnnotations(Annotation[] annotations) {
        if (annotations.length == 0) {
            return ImmutableMap.of();
        }
        ImmutableMap.Builder<Class<? extends Annotation>, Annotation> relevantAnnotations = ImmutableMap.builderWithExpectedSize(annotations.length);
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationCategories.containsKey(annotationType)
                || ignoredMethodAnnotation.equals(annotationType)) {
                relevantAnnotations.put(annotationType, annotation);
            }
        }
        return relevantAnnotations.build();
    }

    private class PropertyAnnotationMetadataBuilder {
        private final String propertyName;
        private final Method getter;
        private final ListMultimap<AnnotationCategory, Annotation> recordedAnnotations;
        private final ImmutableList.Builder<String> problems = ImmutableList.builder();

        public PropertyAnnotationMetadataBuilder(String propertyName, Method getter) {
            this.propertyName = propertyName;
            this.getter = getter;
            this.recordedAnnotations = ArrayListMultimap.create();
        }

        public void recordAnnotation(Annotation annotation) {
            AnnotationCategory category = annotationCategories.get(annotation.annotationType());
            recordedAnnotations.put(category, annotation);
        }

        public void recordProblem(String problem) {
            problems.add(problem);
        }

        public void inherit(Map<AnnotationCategory, Annotation> inheritedAnnotations) {
            inheritedAnnotations.forEach((category, inheritedAnnotation) -> {
                if (!recordedAnnotations.containsKey(category)) {
                    recordedAnnotations.put(category, inheritedAnnotation);
                }
            });
        }

        public PropertyAnnotationMetadata build() {
            ImmutableMap.Builder<AnnotationCategory, Annotation> annotations = ImmutableMap.builder();
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
