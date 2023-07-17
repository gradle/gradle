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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Action;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.annotations.AnnotationCategory;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.ReplayingTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeAwareProblemBuilder;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static org.gradle.api.problems.interfaces.Severity.ERROR;
import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.internal.reflect.Methods.SIGNATURE_EQUIVALENCE;
import static org.gradle.internal.reflect.annotations.AnnotationCategory.TYPE;

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
        public void visitValidationFailures(TypeValidationContext validationContext) {
        }
    };

    private final ImmutableSet<Class<? extends Annotation>> recordedTypeAnnotations;
    private final ImmutableSet<String> ignoredPackagePrefixes;
    private final ImmutableMap<Class<? extends Annotation>, AnnotationCategory> propertyAnnotationCategories;
    private final CrossBuildInMemoryCache<Class<?>, TypeAnnotationMetadata> cache;
    private final ImmutableSet<String> potentiallyIgnoredMethodNames;
    private final ImmutableSet<Equivalence.Wrapper<Method>> globallyIgnoredMethods;
    private final ImmutableSet<Class<?>> mutableNonFinalClasses;
    private final ImmutableSet<Class<? extends Annotation>> ignoredMethodAnnotations;
    private final Predicate<? super Method> generatedMethodDetector;

    /**
     * Constructs the store.
     *
     * @param recordedTypeAnnotations Annotations on the type itself that should be gathered.
     * @param propertyAnnotationCategories Annotations on the properties that should be gathered. They are mapped to {@linkplain AnnotationCategory annotation categories}. The {@code ignoreMethodAnnotation} and the {@literal @}{@link Inject} annotations are automatically mapped to the {@link AnnotationCategory#TYPE TYPE} category.
     * @param ignoredPackagePrefixes Packages to ignore. Types from ignored packages are considered having no type annotations nor any annotated properties.
     * @param ignoredSuperTypes Super-types to ignore. Ignored super-types are considered having no type annotations nor any annotated properties.
     * @param ignoreMethodsFromTypes Methods to ignore: any methods declared by these types are ignored even when overridden by a given type. This is to avoid detecting methods like {@code Object.equals()} or {@code GroovyObject.getMetaClass()}.
     * @param ignoredMethodAnnotations Annotations to use to explicitly ignore a method/property.
     * @param generatedMethodDetector Predicate to test if a method was generated (vs. being provided explicitly by the user).
     * @param mutableNonFinalClasses Mutable classes that shouldn't need explicit setters
     * @param cacheFactory A factory to create cross-build in-memory caches.
     */
    public DefaultTypeAnnotationMetadataStore(
        Collection<Class<? extends Annotation>> recordedTypeAnnotations,
        Map<Class<? extends Annotation>, ? extends AnnotationCategory> propertyAnnotationCategories,
        Collection<String> ignoredPackagePrefixes,
        Collection<Class<?>> ignoredSuperTypes,
        Collection<Class<?>> ignoreMethodsFromTypes,
        Collection<Class<?>> mutableNonFinalClasses,
        Collection<Class<? extends Annotation>> ignoredMethodAnnotations,
        Predicate<? super Method> generatedMethodDetector,
        CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        this.recordedTypeAnnotations = ImmutableSet.copyOf(recordedTypeAnnotations);
        this.ignoredPackagePrefixes = collectIgnoredPackagePrefixes(ignoredPackagePrefixes);
        this.propertyAnnotationCategories = allAnnotationCategories(propertyAnnotationCategories, ignoredMethodAnnotations);
        this.cache = initCache(ignoredSuperTypes, cacheFactory);
        this.potentiallyIgnoredMethodNames = allMethodNamesOf(ignoreMethodsFromTypes);
        this.globallyIgnoredMethods = allMethodsOf(ignoreMethodsFromTypes);
        this.mutableNonFinalClasses = ImmutableSet.copyOf(mutableNonFinalClasses);
        this.ignoredMethodAnnotations = ImmutableSet.copyOf(ignoredMethodAnnotations);
        this.generatedMethodDetector = generatedMethodDetector;
    }

    private static ImmutableSet<String> collectIgnoredPackagePrefixes(Collection<String> ignoredPackagePrefixes) {
        return ImmutableSet.copyOf(ignoredPackagePrefixes.stream()
            .map(prefix -> prefix + ".")
            .collect(Collectors.toList())
        );
    }

    private static ImmutableMap<Class<? extends Annotation>, AnnotationCategory> allAnnotationCategories(
        Map<Class<? extends Annotation>, ? extends AnnotationCategory> propertyAnnotationCategories,
        Collection<Class<? extends Annotation>> ignoredMethodAnnotations
    ) {
        ImmutableMap.Builder<Class<? extends Annotation>, AnnotationCategory> builder = ImmutableMap.builder();
        builder.putAll(propertyAnnotationCategories);
        builder.put(Inject.class, TYPE);
        for (Class<? extends Annotation> ignoredMethodAnnotation : ignoredMethodAnnotations) {
            builder.put(ignoredMethodAnnotation, TYPE);
        }
        return builder.build();
    }

    private static CrossBuildInMemoryCache<Class<?>, TypeAnnotationMetadata> initCache(Collection<Class<?>> ignoredSuperTypes, CrossBuildInMemoryCacheFactory cacheFactory) {
        CrossBuildInMemoryCache<Class<?>, TypeAnnotationMetadata> result = cacheFactory.newClassCache();
        for (Class<?> ignoredSuperType : ignoredSuperTypes) {
            result.put(ignoredSuperType, EMPTY_TYPE_ANNOTATION_METADATA);
        }
        return result;
    }

    private static ImmutableSet<String> allMethodNamesOf(Iterable<Class<?>> classes) {
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
        return cache.get(type, this::createTypeAnnotationMetadata);
    }

    private TypeAnnotationMetadata createTypeAnnotationMetadata(Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isAnnotation()) {
            return EMPTY_TYPE_ANNOTATION_METADATA;
        }

        Package typePackage = type.getPackage();
        if (typePackage != null) {
            String typePackageName = typePackage.getName();
            if (ignoredPackagePrefixes.stream().anyMatch(typePackageName::startsWith)) {
                return EMPTY_TYPE_ANNOTATION_METADATA;
            }
        }

        ImmutableSet.Builder<Annotation> typeAnnotations = ImmutableSet.builder();
        for (Annotation typeAnnotation : type.getDeclaredAnnotations()) {
            if (recordedTypeAnnotations.contains(typeAnnotation.annotationType())) {
                typeAnnotations.add(typeAnnotation);
            }
        }

        Map<String, PropertyAnnotationMetadataBuilder> methodBuilders = new HashMap<>();
        ReplayingTypeValidationContext validationContext = new ReplayingTypeValidationContext();

        inheritMethods(type, validationContext, methodBuilders);

        ImmutableSortedSet<PropertyAnnotationMetadata> propertiesMetadata;
        if (!type.isSynthetic()) {
            propertiesMetadata = extractPropertiesFrom(type, methodBuilders, validationContext);
        } else {
            ImmutableSortedSet.Builder<PropertyAnnotationMetadata> propertiesMetadataBuilder = ImmutableSortedSet.naturalOrder();
            for (PropertyAnnotationMetadataBuilder propertyMetadataBuilder : methodBuilders.values()) {
                propertiesMetadataBuilder.add(propertyMetadataBuilder.build());
            }
            propertiesMetadata = propertiesMetadataBuilder.build();
        }

        return new DefaultTypeAnnotationMetadata(typeAnnotations.build(), propertiesMetadata, validationContext);
    }

    private void inheritMethods(Class<?> type, TypeValidationContext validationContext, Map<String, PropertyAnnotationMetadataBuilder> methodBuilders) {
        visitSuperTypes(type, (superType, metadata) -> {
            for (PropertyAnnotationMetadata property : metadata.getPropertiesAnnotationMetadata()) {
                getOrCreateBuilder(property.getPropertyName(), property.getGetter(), validationContext, methodBuilders)
                    .inheritAnnotations(superType.isInterface(), property);
            }
        });
    }

    private PropertyAnnotationMetadataBuilder getOrCreateBuilder(String propertyName, Method getter, TypeValidationContext validationContext, Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders) {
        return propertyBuilders.computeIfAbsent(getter.getName(), methodName -> new PropertyAnnotationMetadataBuilder(propertyName, getter, validationContext));
    }

    private ImmutableSortedSet<PropertyAnnotationMetadata> extractPropertiesFrom(Class<?> type, Map<String, PropertyAnnotationMetadataBuilder> methodBuilders, TypeValidationContext validationContext) {
        Method[] methods = type.getDeclaredMethods();
        // Make sure getters end up before the setters
        Arrays.sort(methods, comparing(Method::getName));
        for (Method method : methods) {
            processMethodAnnotations(method, methodBuilders, validationContext);
        }

        ImmutableList<PropertyAnnotationMetadataBuilder> propertyBuilders = convertMethodToPropertyBuilders(methodBuilders);
        ImmutableMap<String, ImmutableMap<Class<? extends Annotation>, Annotation>> fieldAnnotationsByPropertyName = collectFieldAnnotations(type);
        return mergePropertiesAndFieldMetadata(type, propertyBuilders, fieldAnnotationsByPropertyName, validationContext);
    }

    private ImmutableList<PropertyAnnotationMetadataBuilder> convertMethodToPropertyBuilders(Map<String, PropertyAnnotationMetadataBuilder> methodBuilders) {
        Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders = new LinkedHashMap<>();
        List<PropertyAnnotationMetadataBuilder> metadataBuilders = Ordering.<PropertyAnnotationMetadataBuilder>from(
                comparing(metadataBuilder -> metadataBuilder.getGetter().getName()))
            .sortedCopy(methodBuilders.values());
        for (PropertyAnnotationMetadataBuilder metadataBuilder : metadataBuilders) {
            String propertyName = metadataBuilder.getPropertyName();
            PropertyAnnotationMetadataBuilder previouslySeenBuilder = propertyBuilders.putIfAbsent(propertyName, metadataBuilder);
            // Do we have an 'is'-getter as well as a 'get'-getter?
            if (previouslySeenBuilder != null) {
                // It is okay to have redundant generated 'is'-getters
                if (generatedMethodDetector.test(metadataBuilder.getter)) {
                    continue;
                }
                // The 'is'-getter is ignored, we can skip it in favor of the 'get'-getter
                if (ignoredMethodAnnotations.stream()
                    .anyMatch(metadataBuilder::hasAnnotation)) {
                    continue;
                }
                // The 'get'-getter was ignored, we can override it with the 'is'`-getter
                if (ignoredMethodAnnotations.stream()
                    .anyMatch(previouslySeenBuilder::hasAnnotation)) {
                    propertyBuilders.put(propertyName, metadataBuilder);
                    continue;
                }
                previouslySeenBuilder.visitPropertyProblem(problem ->
                    problem
                        .forProperty(propertyName)
                        .documentedAt(userManual("validation_problems", "redundant_getters"))
                        .noLocation()
                        .severity(ERROR)
                        .message(String.format("has redundant getters: '%s()' and '%s()'",
                            previouslySeenBuilder.getter.getName(),
                            metadataBuilder.getter.getName()))
                        .type(ValidationProblemId.REDUNDANT_GETTERS.name())
                        .group(ProblemGroup.GENERIC)
                        .description("Boolean property '" + propertyName + "' has both an `is` and a `get` getter")
//                        .withLongDescription("Different annotations on the different getters cause problems on what to track as inputs")
                        .solution("Remove one of the getters")
                        .solution("Annotate one of the getters with @Internal")
                );
            }
        }
        return ImmutableList.copyOf(propertyBuilders.values());
    }

    private ImmutableMap<String, ImmutableMap<Class<? extends Annotation>, Annotation>> collectFieldAnnotations(Class<?> type) {
        ImmutableMap.Builder<String, ImmutableMap<Class<? extends Annotation>, Annotation>> fieldAnnotationsByPropertyName = ImmutableMap.builder();
        for (Field declaredField : type.getDeclaredFields()) {
            if (declaredField.isSynthetic()) {
                continue;
            }
            fieldAnnotationsByPropertyName.put(declaredField.getName(), collectRelevantAnnotations(declaredField));
        }
        return fieldAnnotationsByPropertyName.build();
    }

    private ImmutableSortedSet<PropertyAnnotationMetadata> mergePropertiesAndFieldMetadata(Class<?> type, ImmutableList<PropertyAnnotationMetadataBuilder> propertyBuilders, ImmutableMap<String, ImmutableMap<Class<? extends Annotation>, Annotation>> fieldAnnotationsByPropertyName, TypeValidationContext validationContext) {
        ImmutableSortedSet.Builder<PropertyAnnotationMetadata> propertiesMetadataBuilder = ImmutableSortedSet.naturalOrder();
        ImmutableSet.Builder<String> fieldsSeenBuilder = ImmutableSet.builderWithExpectedSize(fieldAnnotationsByPropertyName.size());
        for (PropertyAnnotationMetadataBuilder metadataBuilder : propertyBuilders) {
            String propertyName = metadataBuilder.getPropertyName();
            ImmutableMap<Class<? extends Annotation>, Annotation> fieldAnnotations = fieldAnnotationsByPropertyName.get(propertyName);
            if (fieldAnnotations != null) {
                fieldsSeenBuilder.add(propertyName);
                for (Annotation annotation : fieldAnnotations.values()) {
                    metadataBuilder.declareAnnotation(annotation);
                }
            }
            propertiesMetadataBuilder.add(metadataBuilder.build());
        }
        ImmutableSortedSet<PropertyAnnotationMetadata> propertiesMetadata = propertiesMetadataBuilder.build();

        // Report fields with annotations that have not been seen while processing properties
        ImmutableSet<String> fieldsSeen = fieldsSeenBuilder.build();
        if (fieldsSeen.size() != fieldAnnotationsByPropertyName.size()) {
            fieldAnnotationsByPropertyName.entrySet().stream()
                .filter(entry -> {
                    String fieldName = entry.getKey();
                    ImmutableMap<Class<? extends Annotation>, Annotation> fieldAnnotations = entry.getValue();
                    return !fieldAnnotations.isEmpty()
                        && !fieldsSeen.contains(fieldName)
                        // @Inject is allowed on fields only
                        && !fieldAnnotations.containsKey(Inject.class);
                })
                .forEach(entry -> {
                    String fieldName = entry.getKey();
                    ImmutableMap<Class<? extends Annotation>, Annotation> fieldAnnotations = entry.getValue();
                    validationContext.visitTypeProblem(problem ->
                        problem
                            .withAnnotationType(type)
                            .documentedAt(userManual("validation_problems", "ignored_annotations_on_field"))
                            .noLocation()
                            .severity(ERROR)
                            .message(String.format("field '%s' without corresponding getter has been annotated with %s", fieldName, simpleAnnotationNames(fieldAnnotations.keySet().stream())))
                            .type(ValidationProblemId.IGNORED_ANNOTATIONS_ON_FIELD.name())
                            .group(ProblemGroup.GENERIC)
                            .description("Annotations on fields are only used if there's a corresponding getter for the field")
                            .solution("Add a getter for field '" + fieldName + "'")
                            .solution("Remove the annotations on '" + fieldName + "'")
                    );
                });
        }
        return propertiesMetadata;
    }

    private boolean shouldIgnore(Method method) {
        if (method.isSynthetic()) {
            return true;
        }
        if (method.isBridge()) {
            return true;
        }
        if (isIgnoredGeneratedGroovyMethod(method)) {
            return true;
        }
        return false;
    }

    /**
     * Groovy getters are annotated with `@Generated` but shouldn't be ignored.
     * Other methods which have '$' in their name are usually a good heuristic for the
     * fact we should ignore them, for example methods generated by the @Memoized annotation
     * ref: https://github.com/gradle/gradle/issues/16117
     *
     * @param method a candidate method
     * @return true if we suspect this method to be a safely ignorable generated method
     */
    private boolean isIgnoredGeneratedGroovyMethod(Method method) {
        return generatedMethodDetector.test(method) && method.getName().contains("$");
    }

    private void processMethodAnnotations(Method method, Map<String, PropertyAnnotationMetadataBuilder> methodBuilders, TypeValidationContext validationContext) {
        if (shouldIgnore(method)) {
            return;
        }
        // As an optimization first check if the method name is among the candidates before we construct an equivalence wrapper
        if (potentiallyIgnoredMethodNames.contains(method.getName())
            && globallyIgnoredMethods.contains(SIGNATURE_EQUIVALENCE.wrap(method))) {
            return;
        }

        ImmutableMap<Class<? extends Annotation>, Annotation> annotations = collectRelevantAnnotations(method);

        if (Modifier.isStatic(method.getModifiers())) {
            validateNotAnnotated(MethodKind.STATIC, method, annotations.keySet(), validationContext);
            return;
        }

        PropertyAccessorType accessorType = PropertyAccessorType.of(method);
        if (accessorType == null) {
            validateNotAnnotated(MethodKind.NON_PROPERTY, method, annotations.keySet(), validationContext);
            return;
        }

        String propertyName = accessorType.propertyNameFor(method);
        if (accessorType == PropertyAccessorType.SETTER) {
            validateNotAnnotated(MethodKind.SETTER, method, annotations.keySet(), validationContext);
            validateSetterForMutableType(method, accessorType, validationContext, propertyName);
            return;
        }

        // After this point we only see getters

        // Ignore private getters without annotations
        boolean privateGetter = Modifier.isPrivate(method.getModifiers());
        if (privateGetter && annotations.isEmpty()) {
            return;
        }

        PropertyAnnotationMetadataBuilder metadataBuilder = getOrCreateBuilder(propertyName, method, validationContext, methodBuilders);
        metadataBuilder.overrideMethod(method);

        if (privateGetter) {
            // At this point we must have annotations on this private getter
            metadataBuilder.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyName)
                    .documentedAt(userManual("validation_problems", "private_getter_must_not_be_annotated"))
                    .noLocation()
                    .severity(ERROR)
                    .message(String.format("is private and annotated with %s", simpleAnnotationNames(annotations.keySet().stream())))
                    .type(ValidationProblemId.PRIVATE_GETTER_MUST_NOT_BE_ANNOTATED.name())
                    .group(ProblemGroup.GENERIC)
                    .description("Annotations on private getters are ignored")
//                    .withLongDescription("Private getters are ignored for up-to-date checking, meaning that you might think you have declared an input when it's not the case")
                    .solution("Make the getter public")
                    .solution("Annotate the public version of the getter")
            );
        }

        for (Annotation annotation : annotations.values()) {
            metadataBuilder.declareAnnotation(annotation);
        }
    }

    private void validateSetterForMutableType(Method setterMethod, PropertyAccessorType setterAccessorType, TypeValidationContext validationContext, String propertyName) {
        Class<?> setterType = setterAccessorType.propertyTypeFor(setterMethod);
        if (isSetterProhibitedForType(setterType)) {
            validationContext.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyName)
                    .documentedAt(userManual("validation_problems", "mutable_type_with_setter"))
                    .noLocation()
                    .severity(ERROR)
                    .message(String.format("of mutable type '%s' is writable", setterType.getName()))
                    .type(ValidationProblemId.MUTABLE_TYPE_WITH_SETTER.name())
                    .group(ProblemGroup.GENERIC)
                    .description("Properties of type '" + setterType.getName() + "' are already mutable")
//                    .withLongDescription(() -> "Setting mutable properties value shouldn't be done by calling a setter but using the mutation methods of '" + setterType.getName() + "', otherwise task dependencies may be lost.")
                    .solution("Remove the '" + setterMethod.getName() + "' method")
            );
        }
    }

    private boolean isSetterProhibitedForType(Class<?> setter) {
        return mutableNonFinalClasses.stream()
            .anyMatch(prohibited -> prohibited.isAssignableFrom(setter));
    }

    private void visitSuperTypes(Class<?> type, TypeAnnotationMetadataVisitor visitor) {
        Arrays.stream(type.getInterfaces())
            .forEach(superInterface -> visitor.visitType(superInterface, getTypeAnnotationMetadata(superInterface)));
        Class<?> superclass = type.getSuperclass();
        if (superclass != null) {
            visitor.visitType(superclass, getTypeAnnotationMetadata(superclass));
        }
    }

    @FunctionalInterface
    private interface TypeAnnotationMetadataVisitor {
        void visitType(Class<?> type, TypeAnnotationMetadata metadata);
    }

    private static void validateNotAnnotated(MethodKind methodKind, Method method, Set<Class<? extends Annotation>> annotationTypes, TypeValidationContext validationContext) {
        if (!annotationTypes.isEmpty()) {
            validationContext.visitTypeProblem(problem ->
                problem.withAnnotationType(method.getDeclaringClass())
                    .documentedAt(userManual("validation_problems", "ignored_annotations_on_method"))
                    .noLocation()
                    .severity(ERROR)
                    .message(String.format("%s '%s()' should not be annotated with: %s",
                        methodKind.getDisplayName(), method.getName(), simpleAnnotationNames(annotationTypes.stream())
                    ))
                    .type(ValidationProblemId.IGNORED_ANNOTATIONS_ON_METHOD.name())
                    .group(ProblemGroup.GENERIC)
                    .description("Input/Output annotations are ignored if they are placed on something else than a getter")
                    .solution("Remove the annotations")
                    .solution("Rename the method")
            );
        }
    }

    private static String simpleAnnotationNames(Stream<Class<? extends Annotation>> annotationTypes) {
        return annotationTypes
            .map(annotationType -> "@" + annotationType.getSimpleName())
            .collect(joining(", "));
    }

    private ImmutableMap<Class<? extends Annotation>, Annotation> collectRelevantAnnotations(AnnotatedElement element) {
        Annotation[] annotations = element.getDeclaredAnnotations();
        if (annotations.length == 0) {
            return ImmutableMap.of();
        }
        ImmutableMap.Builder<Class<? extends Annotation>, Annotation> relevantAnnotations = ImmutableMap.builderWithExpectedSize(annotations.length);
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (propertyAnnotationCategories.containsKey(annotationType)) {
                relevantAnnotations.put(annotationType, annotation);
            }
        }
        return relevantAnnotations.build();
    }

    private class PropertyAnnotationMetadataBuilder implements Comparable<PropertyAnnotationMetadataBuilder> {
        private final String propertyName;
        private Method getter;
        private final ListMultimap<AnnotationCategory, Annotation> declaredAnnotations = MultimapBuilder
            .treeKeys(comparing(AnnotationCategory::getDisplayName))
            .arrayListValues()
            .build();
        private final SetMultimap<AnnotationCategory, Annotation> inheritedInterfaceAnnotations = MultimapBuilder
            .treeKeys(comparing(AnnotationCategory::getDisplayName))
            .linkedHashSetValues()
            .build();
        private final SetMultimap<AnnotationCategory, Annotation> inheritedSuperclassAnnotations = MultimapBuilder
            .treeKeys(comparing(AnnotationCategory::getDisplayName))
            .linkedHashSetValues()
            .build();
        private final TypeValidationContext validationContext;

        public PropertyAnnotationMetadataBuilder(String propertyName, Method getter, TypeValidationContext validationContext) {
            this.propertyName = propertyName;
            this.getter = getter;
            this.validationContext = validationContext;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public Method getGetter() {
            return getter;
        }

        public void overrideMethod(Method method) {
            this.getter = method;
        }

        public void declareAnnotation(Annotation annotation) {
            AnnotationCategory category = propertyAnnotationCategories.get(annotation.annotationType());
            declaredAnnotations.put(category, annotation);
        }

        public void inheritAnnotations(boolean fromInterface, PropertyAnnotationMetadata superProperty) {
            superProperty.getAnnotations()
                .forEach((fromInterface
                    ? inheritedInterfaceAnnotations
                    : inheritedSuperclassAnnotations)::put);
        }

        void visitPropertyProblem(Action<? super TypeAwareProblemBuilder> problemSpec){
            validationContext.visitPropertyProblem(problemSpec);
        }

        public PropertyAnnotationMetadata build() {
            return new DefaultPropertyAnnotationMetadata(propertyName, getter, resolveAnnotations());
        }

        private ImmutableMap<AnnotationCategory, Annotation> resolveAnnotations() {
            // If method should be ignored, then ignore all other annotations
            List<Annotation> declaredTypes = declaredAnnotations.get(TYPE);
            for (Annotation declaredType : declaredTypes) {
                Class<? extends Annotation> ignoredMethodAnnotation = declaredType.annotationType();
                if (ignoredMethodAnnotations.contains(ignoredMethodAnnotation)) {
                    if (declaredAnnotations.values().size() > 1) {
                        visitPropertyProblem(problem ->
                            problem
                                .forProperty(propertyName)
                                .documentedAt(userManual("validation_problems", "ignored_property_must_not_be_annotated"))
                                .noLocation()
                                .severity(ERROR)
                                .message(String.format("annotated with @%s should not be also annotated with %s",
                                    ignoredMethodAnnotation.getSimpleName(),
                                    simpleAnnotationNames(declaredAnnotations.values().stream()
                                        .<Class<? extends Annotation>>map(Annotation::annotationType)
                                        .filter(annotationType -> !annotationType.equals(ignoredMethodAnnotation)))))
                                .type(ValidationProblemId.IGNORED_PROPERTY_MUST_NOT_BE_ANNOTATED.name())
                                .group(ProblemGroup.GENERIC)
                                .description("A property is ignored but also has input annotations")
//                                .withLongDescription("Having both an input annotation and saying that the property is ignored is confusing: Gradle cannot determine if the input is relevant or not")
                                .solution("Remove the input annotations")
                                .solution("Remove the @" + ignoredMethodAnnotation.getSimpleName() + " annotation")
                        );
                    }
                    return ImmutableMap.of(TYPE, declaredType);
                }
            }

            ImmutableMap.Builder<AnnotationCategory, Annotation> builder = ImmutableMap.builder();
            for (AnnotationCategory category : allAnnotationCategories()) {
                Annotation resolvedAnnotation;
                Collection<Annotation> declaredAnnotationsForCategory = declaredAnnotations.get(category);
                if (!declaredAnnotationsForCategory.isEmpty()) {
                    resolvedAnnotation = resolveAnnotation("declared", category, declaredAnnotationsForCategory);
                } else {
                    Collection<Annotation> interfaceAnnotations = inheritedInterfaceAnnotations.get(category);
                    if (!interfaceAnnotations.isEmpty()) {
                        resolvedAnnotation = resolveAnnotation("inherited (from interface)", category, interfaceAnnotations);
                    } else {
                        Collection<Annotation> superclassAnnotations = inheritedSuperclassAnnotations.get(category);
                        resolvedAnnotation = resolveAnnotation("inherited (from superclass)", category, superclassAnnotations);
                    }
                }
                builder.put(category, resolvedAnnotation);
            }
            return builder.build();
        }

        private ImmutableSet<AnnotationCategory> allAnnotationCategories() {
            return ImmutableSet.<AnnotationCategory>builder()
                .addAll(declaredAnnotations.keySet())
                .addAll(inheritedInterfaceAnnotations.keySet())
                .addAll(inheritedSuperclassAnnotations.keySet())
                .build();
        }

        private Annotation resolveAnnotation(String source, AnnotationCategory category, Collection<Annotation> annotationsForCategory) {
            Iterator<Annotation> iDeclaredAnnotationForCategory = annotationsForCategory.iterator();
            // Ignore all but the first recorded annotation
            Annotation declaredAnnotationForCategory = iDeclaredAnnotationForCategory.next();
            if (iDeclaredAnnotationForCategory.hasNext()) {
                visitPropertyProblem(problem ->
                    problem
                        .forProperty(propertyName)
                        .documentedAt(userManual("validation_problems", "conflicting_annotations"))
                        .noLocation()
                        .severity(ERROR)
                        .message(String.format("has conflicting %s annotations %s: %s",
                            category.getDisplayName(),
                            source,
                            simpleAnnotationNames(annotationsForCategory.stream()
                                .map(Annotation::annotationType))
                        ))
                        .type(ValidationProblemId.CONFLICTING_ANNOTATIONS.name())
                        .group(ProblemGroup.GENERIC)
                        .description("The different annotations have different semantics and Gradle cannot determine which one to pick")
                        .solution("Choose between one of the conflicting annotations")
                );
            }
            return declaredAnnotationForCategory;
        }

        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            Iterable<Annotation> allAnnotations = Iterables.concat(
                declaredAnnotations.values(),
                inheritedInterfaceAnnotations.values(),
                inheritedSuperclassAnnotations.values()
            );
            for (Annotation annotation : allAnnotations) {
                if (annotation.annotationType().equals(annotationType)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int compareTo(PropertyAnnotationMetadataBuilder o) {
            return propertyName.compareTo(o.propertyName);
        }
    }

    private enum MethodKind {
        STATIC("static method"),
        NON_PROPERTY("method"),
        SETTER("setter");

        private final String displayName;

        MethodKind(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
