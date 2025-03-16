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

package org.gradle.internal.properties.annotations;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.cache.Cache;
import org.gradle.cache.internal.ClassCacheFactory;
import org.gradle.internal.reflect.annotations.AnnotationCategory;
import org.gradle.internal.reflect.annotations.FunctionAnnotationMetadata;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;
import org.gradle.internal.reflect.validation.ReplayingTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static org.gradle.api.problems.Severity.ERROR;
import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.internal.reflect.annotations.AnnotationCategory.TYPE;

public class DefaultTypeMetadataStore implements TypeMetadataStore {
    private final Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers;
    private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> propertyAnnotationHandlers;
    private final ImmutableMap<Class<? extends Annotation>, ? extends FunctionAnnotationHandler> functionAnnotationHandlers;
    private final ImmutableSet<Class<? extends Annotation>> allowedPropertyModifiers;
    private final ImmutableSet<Class<? extends Annotation>> allowedFunctionModifiers;
    private final Cache<Class<?>, TypeMetadata> cache;
    private final TypeAnnotationMetadataStore typeAnnotationMetadataStore;
    private final PropertyTypeResolver propertyTypeResolver;
    private final String displayName;
    private final MissingPropertyAnnotationHandler missingPropertyAnnotationHandler;

    public DefaultTypeMetadataStore(
        Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers,
        Collection<? extends PropertyAnnotationHandler> propertyAnnotationHandlers,
        Collection<Class<? extends Annotation>> allowedPropertyModifiers,
        Collection<? extends FunctionAnnotationHandler> functionAnnotationHandlers,
        Collection<Class<? extends Annotation>> allowedFunctionModifiers,
        TypeAnnotationMetadataStore typeAnnotationMetadataStore,
        PropertyTypeResolver propertyTypeResolver,
        ClassCacheFactory cacheFactory,
        MissingPropertyAnnotationHandler missingPropertyAnnotationHandler
    ) {
        this.typeAnnotationHandlers = ImmutableSet.copyOf(typeAnnotationHandlers);
        this.propertyAnnotationHandlers = Maps.uniqueIndex(propertyAnnotationHandlers, PropertyAnnotationHandler::getAnnotationType);
        this.allowedPropertyModifiers = ImmutableSet.copyOf(allowedPropertyModifiers);
        this.functionAnnotationHandlers = Maps.uniqueIndex(functionAnnotationHandlers, FunctionAnnotationHandler::getAnnotationType);
        this.allowedFunctionModifiers = ImmutableSet.copyOf(allowedFunctionModifiers);
        this.typeAnnotationMetadataStore = typeAnnotationMetadataStore;
        this.displayName = calculateDisplayName(propertyAnnotationHandlers);
        this.propertyTypeResolver = propertyTypeResolver;
        this.cache = cacheFactory.newClassCache();
        this.missingPropertyAnnotationHandler = missingPropertyAnnotationHandler;
    }

    private static String calculateDisplayName(Collection<? extends PropertyAnnotationHandler> annotationHandlers) {
        return annotationHandlers.stream()
            .map(PropertyAnnotationHandler::getKind)
            .anyMatch(Predicate.isEqual(PropertyAnnotationHandler.Kind.OUTPUT))
            ? "an input or output annotation"
            : "an input annotation";
    }

    @Override
    public <T> TypeMetadata getTypeMetadata(Class<T> type) {
        return cache.get(type, this::createTypeMetadata);
    }


    private static final String ANNOTATION_INVALID_IN_CONTEXT = "ANNOTATION_INVALID_IN_CONTEXT";
    private static final String INCOMPATIBLE_ANNOTATIONS = "INCOMPATIBLE_ANNOTATIONS";

    private <T> TypeMetadata createTypeMetadata(Class<T> type) {
        Class<?> publicType = GeneratedSubclasses.unpack(type);
        ReplayingTypeValidationContext validationContext = new ReplayingTypeValidationContext();
        TypeAnnotationMetadata annotationMetadata = typeAnnotationMetadataStore.getTypeAnnotationMetadata(publicType);
        annotationMetadata.visitValidationFailures(validationContext);

        for (TypeAnnotationHandler annotationHandler : typeAnnotationHandlers) {
            if (annotationMetadata.isAnnotationPresent(annotationHandler.getAnnotationType())) {
                annotationHandler.validateTypeMetadata(publicType, validationContext);
            }
        }

        ImmutableSet<PropertyMetadata> effectiveProperties = getEffectiveProperties(annotationMetadata, validationContext);
        ImmutableSet<FunctionMetadata> effectiveFunctions = getEffectiveFunctions(annotationMetadata, validationContext);
        return new DefaultTypeMetadata(publicType, effectiveProperties, effectiveFunctions, validationContext, propertyAnnotationHandlers, functionAnnotationHandlers, annotationMetadata);
    }

    @NonNull
    private ImmutableSet<PropertyMetadata> getEffectiveProperties(TypeAnnotationMetadata annotationMetadata, ReplayingTypeValidationContext validationContext) {
        ImmutableSet.Builder<PropertyMetadata> effectiveProperties = ImmutableSet.builderWithExpectedSize(annotationMetadata.getPropertiesAnnotationMetadata().size());
        for (PropertyAnnotationMetadata propertyAnnotationMetadata : annotationMetadata.getPropertiesAnnotationMetadata()) {
            Map<AnnotationCategory, Annotation> propertyAnnotations = propertyAnnotationMetadata.getAnnotationsByCategory();
            Class<? extends Annotation> propertyType = propertyTypeResolver.resolveAnnotationType(propertyAnnotations);
            if (propertyType == null) {
                missingPropertyAnnotationHandler.handleMissingPropertyAnnotation(validationContext, propertyAnnotationMetadata, displayName);
                continue;
            }

            PropertyAnnotationHandler annotationHandler = propertyAnnotationHandlers.get(propertyType);
            if (annotationHandler == null) {
                validationContext.visitPropertyProblem(problem ->
                    problem
                        .forProperty(propertyAnnotationMetadata.getPropertyName())
                        .id(TextUtil.screamingSnakeToKebabCase(ANNOTATION_INVALID_IN_CONTEXT), "Invalid annotation in context", GradleCoreProblemGroup.validation().property())
                        .contextualLabel(String.format("is annotated with invalid property type @%s", propertyType.getSimpleName()))
                        .documentedAt(userManual("validation_problems", ANNOTATION_INVALID_IN_CONTEXT.toLowerCase(Locale.ROOT)))
                        .severity(ERROR)
                        .details("The '@" + propertyType.getSimpleName() + "' annotation cannot be used in this context")
                        .solution("Remove the property")
                        .solution("Use a different annotation, e.g one of " + toListOfAnnotations(propertyAnnotationHandlers.keySet()))
                );
                continue;
            }

            ImmutableSet<Class<? extends Annotation>> allowedModifiersForPropertyType = annotationHandler.getAllowedModifiers();
            for (Map.Entry<AnnotationCategory, Annotation> entry : propertyAnnotations.entrySet()) {
                AnnotationCategory annotationCategory = entry.getKey();
                if (annotationCategory == TYPE) {
                    continue;
                }
                Class<? extends Annotation> annotationType = entry.getValue().annotationType();
                if (!allowedModifiersForPropertyType.contains(annotationType)) {
                    validationContext.visitPropertyProblem(problem ->
                        problem
                            .forProperty(propertyAnnotationMetadata.getPropertyName())
                            .id(TextUtil.screamingSnakeToKebabCase(INCOMPATIBLE_ANNOTATIONS), "Incompatible annotations", GradleCoreProblemGroup.validation().property())
                            .contextualLabel("is annotated with @" + annotationType.getSimpleName() + " but that is not allowed for '" + propertyType.getSimpleName() + "' properties")
                            .documentedAt(userManual("validation_problems", INCOMPATIBLE_ANNOTATIONS.toLowerCase(Locale.ROOT)))
                            .severity(ERROR)
                            .details("This modifier is used in conjunction with a property of type '" + propertyType.getSimpleName() + "' but this doesn't have semantics")
                            .solution("Remove the '@" + annotationType.getSimpleName() + "' annotation"));
                } else if (!allowedPropertyModifiers.contains(annotationType)) {
                    validationContext.visitPropertyProblem(problem ->
                        problem
                            .forProperty(propertyAnnotationMetadata.getPropertyName())
                            .id(TextUtil.screamingSnakeToKebabCase(ANNOTATION_INVALID_IN_CONTEXT), "Invalid annotation in context", GradleCoreProblemGroup.validation().property())
                            .contextualLabel(String.format("is annotated with invalid modifier @%s", annotationType.getSimpleName()))
                            .documentedAt(userManual("validation_problems", ANNOTATION_INVALID_IN_CONTEXT.toLowerCase(Locale.ROOT)))
                            .severity(ERROR)
                            .details("The '@" + annotationType.getSimpleName() + "' annotation cannot be used in this context")
                            .solution("Use a different annotation, e.g one of " + toListOfAnnotations(allowedPropertyModifiers))
                            .solution("Remove the annotation")
                    );
                }
            }

            PropertyMetadata property = new DefaultPropertyMetadata(propertyType, propertyAnnotationMetadata);
            annotationHandler.validatePropertyMetadata(property, validationContext);

            if (annotationHandler.isPropertyRelevant()) {
                effectiveProperties.add(property);
            }
        }
        return effectiveProperties.build();
    }

    @NonNull
    private ImmutableSet<FunctionMetadata> getEffectiveFunctions(TypeAnnotationMetadata annotationMetadata, ReplayingTypeValidationContext validationContext) {
        ImmutableSet.Builder<FunctionMetadata> effectiveFunctions = ImmutableSet.builderWithExpectedSize(annotationMetadata.getFunctionAnnotationMetadata().size());
        for (FunctionAnnotationMetadata functionAnnotationMetadata : annotationMetadata.getFunctionAnnotationMetadata()) {
            Map<AnnotationCategory, Annotation> functionAnnotations = functionAnnotationMetadata.getAnnotationsByCategory();
            Annotation functionAnnotation = functionAnnotations.get(TYPE);

            if (functionAnnotation == null) {
                continue;
            }

            Class<? extends Annotation> functionType = functionAnnotation.annotationType();

            FunctionAnnotationHandler annotationHandler = functionAnnotationHandlers.get(functionType);
            if (annotationHandler == null) {
                validationContext.visitPropertyProblem(problem ->
                    problem
                        .forFunction(functionAnnotationMetadata.getMethod().getName())
                        .id(TextUtil.screamingSnakeToKebabCase(ANNOTATION_INVALID_IN_CONTEXT), "Invalid annotation in context", GradleCoreProblemGroup.validation().type())
                        .contextualLabel(String.format("is annotated with invalid function type @%s", functionType.getSimpleName()))
                        .documentedAt(userManual("validation_problems", ANNOTATION_INVALID_IN_CONTEXT.toLowerCase(Locale.ROOT)))
                        .severity(ERROR)
                        .details("The '@" + functionType.getSimpleName() + "' annotation cannot be used in this context")
                        .solution("Remove the method")
                        .solution("Use a different annotation, e.g one of " + toListOfAnnotations(functionAnnotationHandlers.keySet()))
                );
                continue;
            }

            ImmutableSet<Class<? extends Annotation>> allowedModifiersForFunctionType = annotationHandler.getAllowedModifiers();
            for (Map.Entry<AnnotationCategory, Annotation> entry : functionAnnotations.entrySet()) {
                AnnotationCategory annotationCategory = entry.getKey();
                if (annotationCategory == TYPE) {
                    continue;
                }
                Class<? extends Annotation> annotationType = entry.getValue().annotationType();
                if (!allowedModifiersForFunctionType.contains(annotationType)) {
                    validationContext.visitPropertyProblem(problem ->
                        problem
                            .forFunction(functionAnnotationMetadata.getMethod().getName())
                            .id(TextUtil.screamingSnakeToKebabCase(INCOMPATIBLE_ANNOTATIONS), "Incompatible annotations", GradleCoreProblemGroup.validation().type())
                            .contextualLabel("is annotated with @" + annotationType.getSimpleName() + " but that is not allowed for '" + functionType.getSimpleName() + "' functions")
                            .documentedAt(userManual("validation_problems", INCOMPATIBLE_ANNOTATIONS.toLowerCase(Locale.ROOT)))
                            .severity(ERROR)
                            .details("This modifier is used in conjunction with a property of type '" + functionType.getSimpleName() + "' but this doesn't have semantics")
                            .solution("Remove the '@" + annotationType.getSimpleName() + "' annotation"));
                } else if (!allowedFunctionModifiers.contains(annotationType)) {
                    validationContext.visitPropertyProblem(problem ->
                        problem
                            .forProperty(functionAnnotationMetadata.getMethod().getName())
                            .id(TextUtil.screamingSnakeToKebabCase(ANNOTATION_INVALID_IN_CONTEXT), "Invalid annotation in context", GradleCoreProblemGroup.validation().property())
                            .contextualLabel(String.format("is annotated with invalid modifier @%s", annotationType.getSimpleName()))
                            .documentedAt(userManual("validation_problems", ANNOTATION_INVALID_IN_CONTEXT.toLowerCase(Locale.ROOT)))
                            .severity(ERROR)
                            .details("The '@" + annotationType.getSimpleName() + "' annotation cannot be used in this context")
                            .solution("Use a different annotation, e.g one of " + toListOfAnnotations(allowedPropertyModifiers))
                            .solution("Remove the annotation")
                    );
                }
            }

            FunctionMetadata function = new DefaultFunctionMetadata(functionType, functionAnnotationMetadata);
            annotationHandler.validateFunctionMetadata(function, validationContext);

            effectiveFunctions.add(function);
        }
        return effectiveFunctions.build();
    }

    private static String toListOfAnnotations(ImmutableSet<Class<? extends Annotation>> classes) {
        return classes.stream()
            .map(Class::getSimpleName)
            .map(s -> "@" + s)
            .sorted()
            .collect(forDisplay());
    }

    private static class DefaultTypeMetadata implements TypeMetadata {
        private final Class<?> type;
        private final ImmutableSet<PropertyMetadata> propertiesMetadata;
        private final ImmutableSet<FunctionMetadata> functionsMetadata;
        private final ReplayingTypeValidationContext validationProblems;
        private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> propertyAnnotationHandlers;
        private final ImmutableMap<Class<? extends Annotation>, ? extends FunctionAnnotationHandler> functionAnnotationHandlers;
        private final TypeAnnotationMetadata typeAnnotationMetadata;

        DefaultTypeMetadata(
            Class<?> type,
            ImmutableSet<PropertyMetadata> propertiesMetadata,
            ImmutableSet<FunctionMetadata> functionsMetadata,
            ReplayingTypeValidationContext validationProblems,
            ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> propertyAnnotationHandlers,
            ImmutableMap<Class<? extends Annotation>, ? extends FunctionAnnotationHandler> functionAnnotationHandlers,
            TypeAnnotationMetadata typeAnnotationMetadata
        ) {
            this.type = type;
            this.propertiesMetadata = propertiesMetadata;
            this.functionsMetadata = functionsMetadata;
            this.validationProblems = validationProblems;
            this.propertyAnnotationHandlers = propertyAnnotationHandlers;
            this.functionAnnotationHandlers = functionAnnotationHandlers;
            this.typeAnnotationMetadata = typeAnnotationMetadata;
        }

        @Override
        public void visitValidationFailures(@Nullable String ownerPropertyPath, TypeValidationContext validationContext) {
            validationProblems.replay(ownerPropertyPath, validationContext);
        }

        @Override
        public Set<PropertyMetadata> getPropertiesMetadata() {
            return propertiesMetadata;
        }

        @Override
        public Set<FunctionMetadata> getFunctionMetadata() {
            return functionsMetadata;
        }

        @Override
        public boolean hasAnnotatedProperties() {
            return !propertiesMetadata.isEmpty();
        }

        @Override
        public PropertyAnnotationHandler getAnnotationHandlerFor(PropertyMetadata propertyMetadata) {
            return propertyAnnotationHandlers.get(propertyMetadata.getPropertyType());
        }

        @Override
        public FunctionAnnotationHandler getAnnotationHandlerFor(FunctionMetadata functionMetadata) {
            return functionAnnotationHandlers.get(functionMetadata.getFunctionType());
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        @Override
        public TypeAnnotationMetadata getTypeAnnotationMetadata() {
            return typeAnnotationMetadata;
        }
    }

    private static class DefaultPropertyMetadata implements PropertyMetadata {

        private final Class<? extends Annotation> propertyType;
        private final PropertyAnnotationMetadata annotationMetadata;

        public DefaultPropertyMetadata(Class<? extends Annotation> propertyType, PropertyAnnotationMetadata annotationMetadata) {
            this.propertyType = propertyType;
            this.annotationMetadata = annotationMetadata;
        }

        @Override
        public String getPropertyName() {
            return annotationMetadata.getPropertyName();
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return annotationMetadata.isAnnotationPresent(annotationType);
        }

        @Override
        public <T extends Annotation> Optional<T> getAnnotation(Class<T> annotationType) {
            return annotationMetadata.getAnnotation(annotationType);
        }

        @Override
        public Optional<Annotation> getAnnotationForCategory(AnnotationCategory category) {
            return Optional.ofNullable(annotationMetadata.getAnnotationsByCategory().get(category));
        }

        @Override
        public boolean hasAnnotationForCategory(AnnotationCategory category) {
            return annotationMetadata.getAnnotationsByCategory().get(category) != null;
        }

        @Override
        public Class<? extends Annotation> getPropertyType() {
            return propertyType;
        }

        @Override
        public TypeToken<?> getDeclaredType() {
            return annotationMetadata.getDeclaredReturnType();
        }

        @Nullable
        @Override
        public Object getPropertyValue(Object object) {
            return annotationMetadata.getPropertyValue(object);
        }

        @Override
        public String toString() {
            return String.format("@%s %s", propertyType.getSimpleName(), getPropertyName());
        }
    }

    private static class DefaultFunctionMetadata implements FunctionMetadata {
        private final Class<? extends Annotation> functionType;
        private final FunctionAnnotationMetadata annotationMetadata;

        public DefaultFunctionMetadata(Class<? extends Annotation> functionType, FunctionAnnotationMetadata annotationMetadata) {
            this.functionType = functionType;
            this.annotationMetadata = annotationMetadata;
        }

        @Override
        public String getMethodName() {
            return annotationMetadata.getMethod().getName();
        }

        @Override
        public Method getMethod() {
            return annotationMetadata.getMethod();
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return annotationMetadata.isAnnotationPresent(annotationType);
        }

        @Override
        public <T extends Annotation> Optional<T> getAnnotation(Class<T> annotationType) {
            return annotationMetadata.getAnnotation(annotationType);
        }

        @Override
        public Optional<Annotation> getAnnotationForCategory(AnnotationCategory category) {
            return Optional.ofNullable(annotationMetadata.getAnnotationsByCategory().get(category));
        }

        @Override
        public boolean hasAnnotationForCategory(AnnotationCategory category) {
            return annotationMetadata.getAnnotationsByCategory().get(category) != null;
        }

        @Override
        public Class<? extends Annotation> getFunctionType() {
            return functionType;
        }

        @Override
        public TypeToken<?> getDeclaredType() {
            return annotationMetadata.getDeclaredReturnType();
        }

        @Override
        public int hashCode() {
            return annotationMetadata.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return annotationMetadata.equals(obj);
        }

        @Override
        public String toString() {
            return annotationMetadata.toString();
        }
    }

    private static Collector<? super String, ?, String> forDisplay() {
        return Collectors.collectingAndThen(Collectors.toList(), stringList -> {
            if (stringList.isEmpty()) {
                return "";
            }
            if (stringList.size() == 1) {
                return stringList.get(0);
            }
            int bound = stringList.size() - 1;
            return String.join(", ", stringList.subList(0, bound)) + " or " + stringList.get(bound);
        });
    }
}
