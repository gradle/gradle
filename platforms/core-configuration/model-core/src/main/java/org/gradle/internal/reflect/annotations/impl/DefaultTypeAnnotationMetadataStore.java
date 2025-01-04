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
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.annotations.AnnotationCategory;
import org.gradle.internal.reflect.annotations.HasAnnotationMetadata;
import org.gradle.internal.reflect.annotations.FunctionAnnotationMetadata;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;
import org.gradle.internal.reflect.validation.ReplayingTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeAwareProblemBuilder;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.TextUtil;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static org.gradle.api.problems.Severity.ERROR;
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
        public ImmutableSortedSet<FunctionAnnotationMetadata> getFunctionAnnotationMetadata() {
            return ImmutableSortedSet.of();
        }

        @Override
        public void visitValidationFailures(TypeValidationContext validationContext) {
        }

        @Override
        public <T extends Annotation> Optional<T> getAnnotation(Class<T> annotationType) {
            return Optional.empty();
        }
    };

    private final ImmutableSet<Class<? extends Annotation>> recordedTypeAnnotations;
    private final ImmutableSet<String> ignoredPackagePrefixes;
    private final ImmutableMap<Class<? extends Annotation>, AnnotationCategory> propertyAnnotationCategories;
    private final ImmutableMap<Class<? extends Annotation>, AnnotationCategory> functionAnnotationCategories;
    private final CrossBuildInMemoryCache<Class<?>, TypeAnnotationMetadata> cache;
    private final ImmutableSet<String> potentiallyIgnoredMethodNames;
    private final ImmutableSet<Equivalence.Wrapper<Method>> globallyIgnoredMethods;
    private final ImmutableSet<Class<?>> mutableNonFinalClasses;
    private final ImmutableSet<Class<? extends Annotation>> ignoredMethodAnnotations;
    private final ImmutableSet<Class<? extends Annotation>> ignoredMethodAnnotationsAllowedModifiers;
    private final Predicate<? super Method> generatedMethodDetector;

    /**
     * Constructs the store.
     *
     * @param recordedTypeAnnotations Annotations on the type itself that should be gathered.
     * @param propertyAnnotationCategories Annotations on the properties that should be gathered. They are mapped to {@linkplain AnnotationCategory annotation categories}. The {@code ignoredMethodAnnotations} and the {@literal @}{@link Inject} annotations are automatically mapped to the {@link AnnotationCategory#TYPE TYPE} category.
     * @param functionAnnotationCategories Annotations on the functions that should be gathered. They are mapped to {@linkplain AnnotationCategory annotation categories}. The {@code ignoredMethodAnnotations} and the {@literal @}{@link Inject} annotations are automatically mapped to the {@link AnnotationCategory#TYPE TYPE} category.
     * @param ignoredPackagePrefixes Packages to ignore. Types from ignored packages are considered having no type annotations nor any annotated properties.
     * @param ignoredSuperTypes Super-types to ignore. Ignored super-types are considered having no type annotations nor any annotated properties.
     * @param ignoreMethodsFromTypes Methods to ignore: any methods declared by these types are ignored even when overridden by a given type. This is to avoid detecting methods like {@code Object.equals()} or {@code GroovyObject.getMetaClass()}.
     * @param ignoredMethodAnnotations Annotations to use to explicitly ignore a method/property.
     * @param ignoredMethodAnnotationsAllowedModifiers Annotations allowed to be used with the ignore annotations.
     * @param generatedMethodDetector Predicate to test if a method was generated (vs. being provided explicitly by the user).
     * @param mutableNonFinalClasses Mutable classes that shouldn't need explicit setters
     * @param cacheFactory A factory to create cross-build in-memory caches.
     */
    public DefaultTypeAnnotationMetadataStore(
        Collection<Class<? extends Annotation>> recordedTypeAnnotations,
        Map<Class<? extends Annotation>, ? extends AnnotationCategory> propertyAnnotationCategories,
        Map<Class<? extends Annotation>, ? extends AnnotationCategory> functionAnnotationCategories,
        Collection<String> ignoredPackagePrefixes,
        Collection<Class<?>> ignoredSuperTypes,
        Collection<Class<?>> ignoreMethodsFromTypes,
        Collection<Class<?>> mutableNonFinalClasses,
        Collection<Class<? extends Annotation>> ignoredMethodAnnotations,
        Collection<Class<? extends Annotation>> ignoredMethodAnnotationsAllowedModifiers,
        Predicate<? super Method> generatedMethodDetector,
        CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        this.recordedTypeAnnotations = ImmutableSet.copyOf(recordedTypeAnnotations);
        this.ignoredPackagePrefixes = collectIgnoredPackagePrefixes(ignoredPackagePrefixes);
        this.propertyAnnotationCategories = allAnnotationCategoriesForProperties(propertyAnnotationCategories, ignoredMethodAnnotations);
        this.functionAnnotationCategories = allAnnotationCategories(functionAnnotationCategories, Collections.emptyList());
        this.cache = initCache(ignoredSuperTypes, cacheFactory);
        this.potentiallyIgnoredMethodNames = allMethodNamesOf(ignoreMethodsFromTypes);
        this.globallyIgnoredMethods = allMethodsOf(ignoreMethodsFromTypes);
        this.mutableNonFinalClasses = ImmutableSet.copyOf(mutableNonFinalClasses);
        this.ignoredMethodAnnotations = ImmutableSet.copyOf(ignoredMethodAnnotations);
        this.ignoredMethodAnnotationsAllowedModifiers = ImmutableSet.copyOf(ignoredMethodAnnotationsAllowedModifiers);
        this.generatedMethodDetector = generatedMethodDetector;
    }

    private static ImmutableSet<String> collectIgnoredPackagePrefixes(Collection<String> ignoredPackagePrefixes) {
        return ImmutableSet.copyOf(ignoredPackagePrefixes.stream()
            .map(prefix -> prefix + ".")
            .collect(Collectors.toList())
        );
    }

    private static ImmutableMap<Class<? extends Annotation>, AnnotationCategory> allAnnotationCategoriesForProperties(
        Map<Class<? extends Annotation>, ? extends AnnotationCategory> propertyAnnotationCategories,
        Collection<Class<? extends Annotation>> ignoredMethodAnnotations
    ) {
        ImmutableMap.Builder<Class<? extends Annotation>, AnnotationCategory> builder = ImmutableMap.builder();
        builder.putAll(allAnnotationCategories(propertyAnnotationCategories, ignoredMethodAnnotations));
        builder.put(Inject.class, TYPE);
        return builder.build();
    }

    private static ImmutableMap<Class<? extends Annotation>, AnnotationCategory> allAnnotationCategories(
        Map<Class<? extends Annotation>, ? extends AnnotationCategory> annotationCategories,
        Collection<Class<? extends Annotation>> ignoredMethodAnnotations
    ) {
        ImmutableMap.Builder<Class<? extends Annotation>, AnnotationCategory> builder = ImmutableMap.builder();
        builder.putAll(annotationCategories);
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

        Map<String, PropertyAnnotationMetadataBuilder> propertyMethodBuilders = new HashMap<>();
        Map<MethodSignature, FunctionAnnotationMetadataBuilder> functionMethodBuilders = new HashMap<>();
        ReplayingTypeValidationContext validationContext = new ReplayingTypeValidationContext();

        inheritPropertyMethods(type, validationContext, propertyMethodBuilders);
        inheritFunctionMethods(type, validationContext, functionMethodBuilders);

        ImmutableSortedSet<PropertyAnnotationMetadata> propertiesMetadata;
        ImmutableSortedSet<FunctionAnnotationMetadata> functionMetadata;
        if (!type.isSynthetic()) {
            propertiesMetadata = extractPropertiesFrom(type, propertyMethodBuilders, validationContext);
            functionMetadata = extractFunctionsFrom(type, functionMethodBuilders, validationContext);
        } else {
            ImmutableSortedSet.Builder<PropertyAnnotationMetadata> propertiesMetadataBuilder = ImmutableSortedSet.naturalOrder();
            for (PropertyAnnotationMetadataBuilder propertyMetadataBuilder : propertyMethodBuilders.values()) {
                propertiesMetadataBuilder.add(propertyMetadataBuilder.build());
            }
            propertiesMetadata = propertiesMetadataBuilder.build();

            ImmutableSortedSet.Builder<FunctionAnnotationMetadata> functionsMetadataBuilder = ImmutableSortedSet.naturalOrder();
            for (FunctionAnnotationMetadataBuilder functionMetadataBuilder : functionMethodBuilders.values()) {
                functionsMetadataBuilder.add(functionMetadataBuilder.build());
            }
            functionMetadata = functionsMetadataBuilder.build();
        }

        return new DefaultTypeAnnotationMetadata(typeAnnotations.build(), propertiesMetadata, functionMetadata, validationContext);
    }

    private void inheritPropertyMethods(Class<?> type, TypeValidationContext validationContext, Map<String, PropertyAnnotationMetadataBuilder> methodBuilders) {
        visitSuperTypes(type, (superType, metadata) -> {
            for (PropertyAnnotationMetadata property : metadata.getPropertiesAnnotationMetadata()) {
                getOrCreatePropertyBuilder(property.getPropertyName(), property.getMethod(), validationContext, methodBuilders)
                    .inheritAnnotations(superType.isInterface(), property);
            }
        });
    }

    private void inheritFunctionMethods(Class<?> type, TypeValidationContext validationContext, Map<MethodSignature, FunctionAnnotationMetadataBuilder> methodBuilders) {
        visitSuperTypes(type, (superType, metadata) -> {
            for (FunctionAnnotationMetadata method : metadata.getFunctionAnnotationMetadata()) {
                getOrCreateFunctionBuilder(method.getMethod(), validationContext, methodBuilders)
                    .inheritAnnotations(superType.isInterface(), method);
            }
        });
    }

    private PropertyAnnotationMetadataBuilder getOrCreatePropertyBuilder(String propertyName, Method getter, TypeValidationContext validationContext, Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders) {
        return propertyBuilders.computeIfAbsent(getter.getName(), methodName -> new PropertyAnnotationMetadataBuilder(propertyName, getter, validationContext));
    }

    private FunctionAnnotationMetadataBuilder getOrCreateFunctionBuilder(Method method, TypeValidationContext validationContext, Map<MethodSignature, FunctionAnnotationMetadataBuilder> methodBuilders) {
        return methodBuilders.computeIfAbsent(MethodSignature.of(method), methodName -> new FunctionAnnotationMetadataBuilder(method, validationContext));
    }

    private ImmutableSortedSet<PropertyAnnotationMetadata> extractPropertiesFrom(Class<?> type, Map<String, PropertyAnnotationMetadataBuilder> methodBuilders, TypeValidationContext validationContext) {
        Method[] methods = type.getDeclaredMethods();
        // Make sure getters end up before the setters
        Arrays.sort(methods, comparing(Method::getName));
        for (Method method : methods) {
            processPropertyMethodAnnotations(method, methodBuilders, validationContext);
        }

        ImmutableList<PropertyAnnotationMetadataBuilder> propertyBuilders = convertMethodToPropertyBuilders(methodBuilders);
        ImmutableMap<String, ImmutableMap<Class<? extends Annotation>, Annotation>> fieldAnnotationsByPropertyName = collectFieldAnnotations(type, validationContext);
        return mergePropertiesAndFieldMetadata(type, propertyBuilders, fieldAnnotationsByPropertyName, validationContext);
    }

    private ImmutableSortedSet<FunctionAnnotationMetadata> extractFunctionsFrom(Class<?> type, Map<MethodSignature, FunctionAnnotationMetadataBuilder> methodBuilders, TypeValidationContext validationContext) {
        Method[] methods = type.getDeclaredMethods();
        // Make sure getters end up before the setters
        Arrays.sort(methods, comparing(Method::getName));
        for (Method method : methods) {
            processFunctionMethodAnnotations(method, methodBuilders, validationContext);
        }

        ImmutableSortedSet.Builder<FunctionAnnotationMetadata> methodsMetadataBuilder = ImmutableSortedSet.naturalOrder();
        methodBuilders.values().forEach(metadataBuilder -> methodsMetadataBuilder.add(metadataBuilder.build()));
        return methodsMetadataBuilder.build();
    }

    private static final String REDUNDANT_GETTERS = "REDUNDANT_GETTERS";

    private ImmutableList<PropertyAnnotationMetadataBuilder> convertMethodToPropertyBuilders(Map<String, PropertyAnnotationMetadataBuilder> methodBuilders) {
        Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders = new LinkedHashMap<>();
        List<PropertyAnnotationMetadataBuilder> metadataBuilders = Ordering.<PropertyAnnotationMetadataBuilder>from(
                comparing(metadataBuilder -> metadataBuilder.getMethod().getName()))
            .sortedCopy(methodBuilders.values());
        for (PropertyAnnotationMetadataBuilder metadataBuilder : metadataBuilders) {
            String propertyName = metadataBuilder.getPropertyName();
            PropertyAnnotationMetadataBuilder previouslySeenBuilder = propertyBuilders.putIfAbsent(propertyName, metadataBuilder);
            // Do we have an 'is'-getter as well as a 'get'-getter? (`previouslySeenBuilder` is the 'get'-getter if so, due to previous sorting)
            if (previouslySeenBuilder != null) {
                // It is okay to have redundant generated 'is'-getters
                if (generatedMethodDetector.test(metadataBuilder.getMethod())) {
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
                        .id(TextUtil.screamingSnakeToKebabCase(REDUNDANT_GETTERS), "Property has redundant getters", GradleCoreProblemGroup.validation().property()) // TODO (donat) missing test coverage
                        .contextualLabel(
                            String.format(
                                "has redundant getters: '%s()' and '%s()'",
                                previouslySeenBuilder.getMethod().getName(),
                                metadataBuilder.getMethod().getName()
                            )
                        )
                        .documentedAt(userManual("validation_problems", REDUNDANT_GETTERS.toLowerCase(Locale.ROOT)))
                        .severity(ERROR)
                        .details("Boolean property '" + propertyName + "' has both an `is` and a `get` getter")
                        .solution("Remove one of the getters")
                        .solution("Annotate one of the getters with @Internal")
                );
            } else {
                // Warn on Boolean is-getters that are not ignored and do not have a corresponding get-getter
                Method method = metadataBuilder.getter;
                if (PropertyAccessorType.of(method) == PropertyAccessorType.IS_GETTER && method.getReturnType() == Boolean.class && ignoredMethodAnnotations.stream().noneMatch(metadataBuilder::hasAnnotation)) {
                    DeprecationLogger.deprecateAction("Declaring an 'is-' property with a Boolean type")
                        .withAdvice(String.format(
                            "Add a method named '%s' with the same behavior and mark the old one with @Deprecated and @ReplacedBy, or change the type of '%s.%s' (and the setter) to 'boolean'.",
                            method.getName().replace("is", "get"),
                            method.getDeclaringClass().getCanonicalName(), method.getName()
                        ))
                        .withContext("The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy.")
                        .startingWithGradle9("this property will be ignored by Gradle")
                        .withUpgradeGuideSection(8, "groovy_boolean_properties")
                        .nagUser();
                }
            }
        }
        return ImmutableList.copyOf(propertyBuilders.values());
    }

    private ImmutableMap<String, ImmutableMap<Class<? extends Annotation>, Annotation>> collectFieldAnnotations(Class<?> type, TypeValidationContext validationContext) {
        ImmutableMap.Builder<String, ImmutableMap<Class<? extends Annotation>, Annotation>> fieldAnnotationsByPropertyName = ImmutableMap.builder();
        for (Field declaredField : type.getDeclaredFields()) {
            if (declaredField.isSynthetic()) {
                continue;
            }
            fieldAnnotationsByPropertyName.put(declaredField.getName(), collectRelevantAnnotations(declaredField, propertyAnnotationCategories));
            ImmutableMap<Class<? extends Annotation>, Annotation> nonPropertyAnnotations = collectRelevantAnnotations(declaredField, functionAnnotationCategories);
            if (!nonPropertyAnnotations.isEmpty()) {
                // Function method annotations should not be applicable to fields and should throw a compile time error, but in the
                // event that they are marked applicable to fields, we report them as a problem.  If we add an annotation that is somehow
                // valid in this context, we'll need to handle it in some way to avoid the problem being generated.
                validationContext.visitTypeProblem(problem ->
                    problem.withAnnotationType(declaredField.getDeclaringClass())
                        .id(TextUtil.screamingSnakeToKebabCase(IGNORED_ANNOTATIONS_ON_PROPERTY), "Ignored annotations on property", GradleCoreProblemGroup.validation().type())
                        .contextualLabel(
                            String.format(
                                "field '%s()' should not be annotated with: %s",
                                declaredField.getName(),
                                simpleAnnotationNames(nonPropertyAnnotations.keySet().stream())
                            )
                        )
                        .documentedAt(userManual("validation_problems", IGNORED_ANNOTATIONS_ON_PROPERTY.toLowerCase(Locale.ROOT)))
                        .severity(ERROR)
                        .details("Function annotations are ignored if they are placed on a field")
                        .solution("Remove the annotations")
                );
            }
        }
        return fieldAnnotationsByPropertyName.build();
    }

    private static final String IGNORED_ANNOTATIONS_ON_FIELD = "IGNORED_ANNOTATIONS_ON_FIELD";

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
                            .id(TextUtil.screamingSnakeToKebabCase(IGNORED_ANNOTATIONS_ON_FIELD), "Incorrect annotations on field", GradleCoreProblemGroup.validation().property()) // TODO (donat) missing test coverage
                            .contextualLabel(
                                String.format(
                                    "field '%s' without corresponding getter has been annotated with %s",
                                    fieldName,
                                    simpleAnnotationNames(fieldAnnotations.keySet().stream())
                                )
                            )
                            .documentedAt(userManual("validation_problems", IGNORED_ANNOTATIONS_ON_FIELD.toLowerCase(Locale.ROOT)))
                            .severity(ERROR)
                            .details("Annotations on fields are only used if there's a corresponding getter for the field")
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

    private static final String PRIVATE_GETTER_MUST_NOT_BE_ANNOTATED = "PRIVATE_GETTER_MUST_NOT_BE_ANNOTATED";

    private void processPropertyMethodAnnotations(Method method, Map<String, PropertyAnnotationMetadataBuilder> propertyBuilders, TypeValidationContext validationContext) {
        if (shouldIgnore(method)) {
            return;
        }
        // As an optimization first check if the method name is among the candidates before we construct an equivalence wrapper
        if (potentiallyIgnoredMethodNames.contains(method.getName())
            && globallyIgnoredMethods.contains(SIGNATURE_EQUIVALENCE.wrap(method))) {
            return;
        }

        ImmutableMap<Class<? extends Annotation>, Annotation> annotations = collectRelevantAnnotations(method, propertyAnnotationCategories);

        if (Modifier.isStatic(method.getModifiers())) {
            validateNotAnnotatedForProperty(MethodKind.STATIC, method, annotations.keySet(), validationContext);
            return;
        }

        PropertyAccessorType accessorType = PropertyAccessorType.of(method);
        if (accessorType == null) {
            validateNotAnnotatedForProperty(MethodKind.FUNCTION, method, annotations.keySet(), validationContext);
            return;
        }

        String propertyName = accessorType.propertyNameFor(method);
        if (accessorType == PropertyAccessorType.SETTER) {
            validateNotAnnotatedForProperty(MethodKind.SETTER, method, annotations.keySet(), validationContext);
            validateSetterForMutableType(method, accessorType, validationContext, propertyName);
            return;
        }

        // After this point we only see getters

        // Ignore private getters without annotations
        boolean privateGetter = Modifier.isPrivate(method.getModifiers());
        if (privateGetter && annotations.isEmpty()) {
            return;
        }

        PropertyAnnotationMetadataBuilder metadataBuilder = getOrCreatePropertyBuilder(propertyName, method, validationContext, propertyBuilders);
        metadataBuilder.overrideMethod(method);

        if (privateGetter) {
            // At this point we must have annotations on this private getter
            metadataBuilder.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyName)
                    .id(TextUtil.screamingSnakeToKebabCase(PRIVATE_GETTER_MUST_NOT_BE_ANNOTATED), "Private property with wrong annotation", GradleCoreProblemGroup.validation().property())
                    .contextualLabel(String.format("is private and annotated with %s", simpleAnnotationNames(annotations.keySet().stream())))
                    .documentedAt(userManual("validation_problems", PRIVATE_GETTER_MUST_NOT_BE_ANNOTATED.toLowerCase(Locale.ROOT)))
                    .severity(ERROR)
                    .details("Annotations on private getters are ignored")
                    .solution("Make the getter public")
                    .solution("Annotate the public version of the getter")
            );
        }

        for (Annotation annotation : annotations.values()) {
            metadataBuilder.declareAnnotation(annotation);
        }
    }

    private static final String PRIVATE_METHOD_MUST_NOT_BE_ANNOTATED = "PRIVATE_METHOD_MUST_NOT_BE_ANNOTATED";

    private void processFunctionMethodAnnotations(Method method, Map<MethodSignature, FunctionAnnotationMetadataBuilder> functionBuilders, TypeValidationContext validationContext) {
        if (shouldIgnore(method)) {
            return;
        }
        // As an optimization first check if the method name is among the candidates before we construct an equivalence wrapper
        if (potentiallyIgnoredMethodNames.contains(method.getName())
            && globallyIgnoredMethods.contains(SIGNATURE_EQUIVALENCE.wrap(method))) {
            return;
        }

        ImmutableMap<Class<? extends Annotation>, Annotation> annotations = collectRelevantAnnotations(method, functionAnnotationCategories);

        // If the function method is not annotated, we can ignore it
        if (annotations.isEmpty()) {
            return;
        }

        if (Modifier.isStatic(method.getModifiers())) {
            validateNotAnnotatedForStaticFunction(method, annotations.keySet(), validationContext);
            return;
        }

        PropertyAccessorType accessorType = PropertyAccessorType.of(method);
        if (accessorType != null) {
            validateNotAnnotatedForPropertyGetter(method, annotations.keySet(), validationContext);
            return;
        }

        FunctionAnnotationMetadataBuilder metadataBuilder = getOrCreateFunctionBuilder(method, validationContext, functionBuilders);
        metadataBuilder.overrideMethod(method);

        boolean privateMethod = Modifier.isPrivate(method.getModifiers());
        if (privateMethod) {
            // At this point we must have annotations on this private getter
            metadataBuilder.visitFunctionProblem(problem ->
                problem
                    .forFunction(method.getName())
                    .id(TextUtil.screamingSnakeToKebabCase(PRIVATE_METHOD_MUST_NOT_BE_ANNOTATED), "Private method with wrong annotation", GradleCoreProblemGroup.validation().property())
                    .contextualLabel(String.format("is private and annotated with %s", simpleAnnotationNames(annotations.keySet().stream())))
                    .documentedAt(userManual("validation_problems", PRIVATE_METHOD_MUST_NOT_BE_ANNOTATED.toLowerCase(Locale.ROOT)))
                    .severity(ERROR)
                    .details("Annotations on private methods are ignored")
                    .solution("Make the method public")
                    .solution("Annotate the public version of the method")
            );
        }

        for (Annotation annotation : annotations.values()) {
            metadataBuilder.declareAnnotation(annotation);
        }
    }

    private static final String MUTABLE_TYPE_WITH_SETTER = "MUTABLE_TYPE_WITH_SETTER";

    private void validateSetterForMutableType(Method setterMethod, PropertyAccessorType setterAccessorType, TypeValidationContext validationContext, String propertyName) {
        Class<?> setterType = setterAccessorType.propertyTypeFor(setterMethod);
        if (isSetterProhibitedForType(setterType)) {
            validationContext.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyName)
                    .id(TextUtil.screamingSnakeToKebabCase(MUTABLE_TYPE_WITH_SETTER), "Mutable type with setter", GradleCoreProblemGroup.validation().property())
                    .contextualLabel(String.format("of mutable type '%s' is writable", setterType.getName()))
                    .documentedAt(userManual("validation_problems", MUTABLE_TYPE_WITH_SETTER.toLowerCase(Locale.ROOT)))
                    .severity(ERROR)
                    .details("Properties of type '" + setterType.getName() + "' are already mutable")
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

    private static final String IGNORED_ANNOTATIONS_ON_METHOD = "IGNORED_ANNOTATIONS_ON_METHOD";

    private static void validateNotAnnotatedForProperty(MethodKind methodKind, Method method, Set<Class<? extends Annotation>> annotationTypes, TypeValidationContext validationContext) {
        if (!annotationTypes.isEmpty()) {
            validationContext.visitTypeProblem(problem ->
                problem.withAnnotationType(method.getDeclaringClass())
                    .id(TextUtil.screamingSnakeToKebabCase(IGNORED_ANNOTATIONS_ON_METHOD), "Ignored annotations on method", GradleCoreProblemGroup.validation().type())
                    .contextualLabel(
                        String.format(
                            "%s '%s()' should not be annotated with: %s",
                            methodKind.getDisplayName(), method.getName(),
                            simpleAnnotationNames(annotationTypes.stream())
                        )
                    )
                    .documentedAt(userManual("validation_problems", IGNORED_ANNOTATIONS_ON_METHOD.toLowerCase(Locale.ROOT)))
                    .severity(ERROR)
                    .details("Input/Output annotations are ignored if they are placed on something else than a getter")
                    .solution("Remove the annotations")
                    .solution("Rename the method")
            );
        }
    }

    private static final String IGNORED_ANNOTATIONS_ON_PROPERTY = "IGNORED_ANNOTATIONS_ON_PROPERTY";

    private static void validateNotAnnotatedForPropertyGetter(Method method, Set<Class<? extends Annotation>> annotationTypes, TypeValidationContext validationContext) {
        if (!annotationTypes.isEmpty()) {
            validationContext.visitTypeProblem(problem ->
                problem.withAnnotationType(method.getDeclaringClass())
                    .id(TextUtil.screamingSnakeToKebabCase(IGNORED_ANNOTATIONS_ON_PROPERTY), "Ignored annotations on property", GradleCoreProblemGroup.validation().type())
                    .contextualLabel(
                        String.format(
                            "%s '%s()' should not be annotated with: %s",
                            MethodKind.PROPERTY.getDisplayName(), method.getName(),
                            simpleAnnotationNames(annotationTypes.stream())
                        )
                    )
                    .documentedAt(userManual("validation_problems", IGNORED_ANNOTATIONS_ON_PROPERTY.toLowerCase(Locale.ROOT)))
                    .severity(ERROR)
                    .details("Function annotations are ignored if they are placed on a property getter")
                    .solution("Remove the annotations")
                    .solution("Rename the method")
            );
        }
    }

    private static void validateNotAnnotatedForStaticFunction(Method method, Set<Class<? extends Annotation>> annotationTypes, TypeValidationContext validationContext) {
        if (!annotationTypes.isEmpty()) {
            validationContext.visitTypeProblem(problem ->
                problem.withAnnotationType(method.getDeclaringClass())
                    .id(TextUtil.screamingSnakeToKebabCase(IGNORED_ANNOTATIONS_ON_PROPERTY), "Ignored annotations on property", GradleCoreProblemGroup.validation().type())
                    .contextualLabel(
                        String.format(
                            "%s '%s()' should not be annotated with: %s",
                            MethodKind.STATIC.getDisplayName(), method.getName(),
                            simpleAnnotationNames(annotationTypes.stream())
                        )
                    )
                    .documentedAt(userManual("validation_problems", IGNORED_ANNOTATIONS_ON_PROPERTY.toLowerCase(Locale.ROOT)))
                    .severity(ERROR)
                    .details("Function annotations are ignored if they are placed on a static method")
                    .solution("Remove the annotations")
                    .solution("Make the method non-static")
            );
        }
    }

    private static String simpleAnnotationNames(Stream<Class<? extends Annotation>> annotationTypes) {
        return annotationTypes
            .map(annotationType -> "@" + annotationType.getSimpleName())
            .collect(joining(", "));
    }

    private static ImmutableMap<Class<? extends Annotation>, Annotation> collectRelevantAnnotations(AnnotatedElement element, ImmutableMap<Class<? extends Annotation>, AnnotationCategory> relevantCategories) {
        Annotation[] annotations = element.getDeclaredAnnotations();
        if (annotations.length == 0) {
            return ImmutableMap.of();
        }
        ImmutableMap.Builder<Class<? extends Annotation>, Annotation> relevantAnnotations = ImmutableMap.builderWithExpectedSize(annotations.length);
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (relevantCategories.containsKey(annotationType)) {
                relevantAnnotations.put(annotationType, annotation);
            }
        }
        return relevantAnnotations.build();
    }

    private static abstract class HasAnnotationMetadataBuilder {
        protected static final String IGNORED_PROPERTY_MUST_NOT_BE_ANNOTATED = "IGNORED_PROPERTY_MUST_NOT_BE_ANNOTATED";
        protected static final String CONFLICTING_ANNOTATIONS = "CONFLICTING_ANNOTATIONS";
        protected final ListMultimap<AnnotationCategory, Annotation> declaredAnnotations = MultimapBuilder
            .treeKeys(comparing(AnnotationCategory::getDisplayName))
            .arrayListValues()
            .build();
        protected final SetMultimap<AnnotationCategory, Annotation> inheritedInterfaceAnnotations = MultimapBuilder
            .treeKeys(comparing(AnnotationCategory::getDisplayName))
            .linkedHashSetValues()
            .build();
        protected final SetMultimap<AnnotationCategory, Annotation> inheritedSuperclassAnnotations = MultimapBuilder
            .treeKeys(comparing(AnnotationCategory::getDisplayName))
            .linkedHashSetValues()
            .build();
        protected final TypeValidationContext validationContext;
        protected Method method;

        private HasAnnotationMetadataBuilder(Method method, TypeValidationContext validationContext) {
            overrideMethod(method);
            this.validationContext = validationContext;
        }

        public Method getMethod() {
            return method;
        }

        public void overrideMethod(Method method) {
            this.method = method;
        }

        public void declareAnnotation(Annotation annotation) {
            AnnotationCategory category = geAnnotationCategories().get(annotation.annotationType());
            declaredAnnotations.put(category, annotation);
        }

        public void inheritAnnotations(boolean fromInterface, HasAnnotationMetadata superProperty) {
            superProperty.getAnnotationsByCategory()
                .forEach((fromInterface
                    ? inheritedInterfaceAnnotations
                    : inheritedSuperclassAnnotations)::put);
        }

        protected ImmutableSet<AnnotationCategory> allAnnotationCategories() {
            return ImmutableSet.<AnnotationCategory>builder()
                .addAll(declaredAnnotations.keySet())
                .addAll(inheritedInterfaceAnnotations.keySet())
                .addAll(inheritedSuperclassAnnotations.keySet())
                .build();
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

        protected ImmutableMap<AnnotationCategory, Annotation> resolveAnnotations() {
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

        private Annotation resolveAnnotation(String source, AnnotationCategory category, Collection<Annotation> annotationsForCategory) {
            Iterator<Annotation> declaredAnnotationForCategoryIterator = annotationsForCategory.iterator();
            // Ignore all but the first recorded annotation
            Annotation declaredAnnotationForCategory = declaredAnnotationForCategoryIterator.next();
            if (declaredAnnotationForCategoryIterator.hasNext()) {
                handleConflictingAnnotation(source, category, annotationsForCategory);
            }
            return declaredAnnotationForCategory;
        }

        abstract protected ImmutableMap<Class<? extends Annotation>, AnnotationCategory> geAnnotationCategories();

        abstract protected void handleConflictingAnnotation(String source, AnnotationCategory category, Collection<Annotation> annotationsForCategory);
    }

    private class PropertyAnnotationMetadataBuilder extends HasAnnotationMetadataBuilder implements Comparable<PropertyAnnotationMetadataBuilder> {
        private final String propertyName;

        public PropertyAnnotationMetadataBuilder(String propertyName, Method getter, TypeValidationContext validationContext) {
            super(getter, validationContext);
            this.propertyName = propertyName;
        }

        public String getPropertyName() {
            return propertyName;
        }

        @Override
        protected ImmutableMap<Class<? extends Annotation>, AnnotationCategory> geAnnotationCategories() {
            return propertyAnnotationCategories;
        }

        public PropertyAnnotationMetadata build() {
            return new DefaultPropertyAnnotationMetadata(propertyName, getMethod(), resolveAnnotations());
        }

        private void visitPropertyProblem(Action<? super TypeAwareProblemBuilder> problemSpec) {
            validationContext.visitPropertyProblem(problemSpec);
        }

        @Override
        protected ImmutableMap<AnnotationCategory, Annotation> resolveAnnotations() {
            // If method should be ignored, then ignore all other annotations
            List<Annotation> declaredTypes = declaredAnnotations.get(TYPE);
            for (Annotation declaredType : declaredTypes) {
                Class<? extends Annotation> ignoredMethodAnnotation = declaredType.annotationType();
                if (ignoredMethodAnnotations.contains(ignoredMethodAnnotation)) {
                    if (declaredAnnotations.values().size() > 1 && ignoreAnnotationDisallowedModifiers(declaredAnnotations.values()).count() > 1) {
                        handleAnnotatedIgnoredMethod(ignoredMethodAnnotation);
                    }
                    return ImmutableMap.of(TYPE, declaredType);
                }
            }

            return super.resolveAnnotations();
        }

        private Stream<Annotation> ignoreAnnotationDisallowedModifiers(Collection<Annotation> annotations) {
            return annotations.stream().filter(annotation -> !ignoredMethodAnnotationsAllowedModifiers.contains(annotation.annotationType()));
        }

        private void handleAnnotatedIgnoredMethod(Class<? extends Annotation> ignoredMethodAnnotation) {
            visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyName)
                    .id(TextUtil.screamingSnakeToKebabCase(IGNORED_PROPERTY_MUST_NOT_BE_ANNOTATED), "Has wrong combination of annotations", GradleCoreProblemGroup.validation().property())
                    .contextualLabel(
                        String.format(
                            "annotated with @%s should not be also annotated with %s",
                            ignoredMethodAnnotation.getSimpleName(),
                            simpleAnnotationNames(ignoreAnnotationDisallowedModifiers(declaredAnnotations.values())
                                .<Class<? extends Annotation>>map(Annotation::annotationType)
                                .filter(annotationType -> !annotationType.equals(ignoredMethodAnnotation)))
                        )
                    )
                    .documentedAt(userManual("validation_problems", IGNORED_PROPERTY_MUST_NOT_BE_ANNOTATED.toLowerCase(Locale.ROOT)))
                    .severity(ERROR)
                    .details("A property is ignored but also has input annotations")
                    .solution("Remove the input annotations")
                    .solution("Remove the @" + ignoredMethodAnnotation.getSimpleName() + " annotation")
            );
        }

        @Override
        protected void handleConflictingAnnotation(String source, AnnotationCategory category, Collection<Annotation> annotationsForCategory) {
            visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyName)
                    .id(TextUtil.screamingSnakeToKebabCase(CONFLICTING_ANNOTATIONS), StringUtils.capitalize(category.getDisplayName()) + " has conflicting annotation", GradleCoreProblemGroup.validation().property())
                    .contextualLabel(
                        String.format(
                            "has conflicting %s annotations %s: %s",
                            category.getDisplayName(),
                            source,
                            simpleAnnotationNames(annotationsForCategory.stream().map(Annotation::annotationType))
                        )
                    )
                    .documentedAt(userManual("validation_problems", CONFLICTING_ANNOTATIONS.toLowerCase(Locale.ROOT)))
                    .severity(ERROR)
                    .details("The different annotations have different semantics and Gradle cannot determine which one to pick")
                    .solution("Choose between one of the conflicting annotations")
            );
        }

        @Override
        public int compareTo(PropertyAnnotationMetadataBuilder o) {
            return propertyName.compareTo(o.propertyName);
        }
    }

    private class FunctionAnnotationMetadataBuilder extends HasAnnotationMetadataBuilder implements Comparable<FunctionAnnotationMetadataBuilder> {
        public FunctionAnnotationMetadataBuilder(Method method, TypeValidationContext validationContext) {
            super(method, validationContext);
        }

        @Override
        protected ImmutableMap<Class<? extends Annotation>, AnnotationCategory> geAnnotationCategories() {
            return functionAnnotationCategories;
        }

        public FunctionAnnotationMetadata build() {
            return new DefaultFunctionAnnotationMetadata(getMethod(), resolveAnnotations());
        }

        private void visitFunctionProblem(Action<? super TypeAwareProblemBuilder> problemSpec) {
            validationContext.visitTypeProblem(problemSpec);
        }

        @Override
        protected void handleConflictingAnnotation(String source, AnnotationCategory category, Collection<Annotation> annotationsForCategory) {
            visitFunctionProblem(problem ->
                problem
                    .forFunction(getMethod().getName())
                    .id(TextUtil.screamingSnakeToKebabCase(CONFLICTING_ANNOTATIONS), StringUtils.capitalize(category.getDisplayName()) + " has conflicting annotation", GradleCoreProblemGroup.validation().type())
                    .contextualLabel(
                        String.format(
                            "has conflicting %s annotations %s: %s",
                            category.getDisplayName(),
                            source,
                            simpleAnnotationNames(annotationsForCategory.stream().map(Annotation::annotationType))
                        )
                    )
                    .documentedAt(userManual("validation_problems", CONFLICTING_ANNOTATIONS.toLowerCase(Locale.ROOT)))
                    .severity(ERROR)
                    .details("The different annotations have different semantics and Gradle cannot determine which one to pick")
                    .solution("Choose between one of the conflicting annotations")
            );
        }

        @Override
        public int compareTo(FunctionAnnotationMetadataBuilder o) {
            return getMethod().getName().compareTo(o.getMethod().getName());
        }
    }

    private enum MethodKind {
        STATIC("static method"),
        PROPERTY("property"),
        FUNCTION("function"),
        SETTER("setter");

        private final String displayName;

        MethodKind(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static class MethodSignature {
        private final String name;
        private final Class<?>[] parameterTypes;

        public MethodSignature(String name, Class<?>[] parameterTypes) {
            this.name = name;
            this.parameterTypes = parameterTypes;
        }

        public static MethodSignature of(Method method) {
            return new MethodSignature(method.getName(), method.getParameterTypes());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MethodSignature that = (MethodSignature) o;
            return name.equals(that.name) && Arrays.equals(parameterTypes, that.parameterTypes);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + Arrays.hashCode(parameterTypes);
            return result;
        }
    }
}
