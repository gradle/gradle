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
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.annotations.AnnotationCategory;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;
import org.gradle.internal.reflect.validation.ReplayingTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static org.gradle.api.problems.internal.DefaultProblemCategory.VALIDATION;
import static org.gradle.api.problems.Severity.ERROR;
import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.internal.reflect.annotations.AnnotationCategory.TYPE;

public class DefaultTypeMetadataStore implements TypeMetadataStore {
    private final Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers;
    private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> propertyAnnotationHandlers;
    private final ImmutableSet<Class<? extends Annotation>> allowedPropertyModifiers;
    private final CrossBuildInMemoryCache<Class<?>, TypeMetadata> cache;
    private final TypeAnnotationMetadataStore typeAnnotationMetadataStore;
    private final PropertyTypeResolver propertyTypeResolver;
    private final String displayName;

    public DefaultTypeMetadataStore(
        Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers,
        Collection<? extends PropertyAnnotationHandler> propertyAnnotationHandlers,
        Collection<Class<? extends Annotation>> allowedPropertyModifiers,
        TypeAnnotationMetadataStore typeAnnotationMetadataStore,
        PropertyTypeResolver propertyTypeResolver,
        CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        this.typeAnnotationHandlers = ImmutableSet.copyOf(typeAnnotationHandlers);
        this.propertyAnnotationHandlers = Maps.uniqueIndex(propertyAnnotationHandlers, PropertyAnnotationHandler::getAnnotationType);
        this.allowedPropertyModifiers = ImmutableSet.copyOf(allowedPropertyModifiers);
        this.typeAnnotationMetadataStore = typeAnnotationMetadataStore;
        this.displayName = calculateDisplayName(propertyAnnotationHandlers);
        this.propertyTypeResolver = propertyTypeResolver;
        this.cache = cacheFactory.newClassCache();
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
    private static final String MISSING_ANNOTATION = "MISSING_ANNOTATION";
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

        ImmutableSet.Builder<PropertyMetadata> effectiveProperties = ImmutableSet.builderWithExpectedSize(annotationMetadata.getPropertiesAnnotationMetadata().size());
        for (PropertyAnnotationMetadata propertyAnnotationMetadata : annotationMetadata.getPropertiesAnnotationMetadata()) {
            Map<AnnotationCategory, Annotation> propertyAnnotations = propertyAnnotationMetadata.getAnnotations();
            Class<? extends Annotation> propertyType = propertyTypeResolver.resolveAnnotationType(propertyAnnotations);
            if (propertyType == null) {
                validationContext.visitPropertyProblem(problem ->
                    problem
                        .forProperty(propertyAnnotationMetadata.getPropertyName())
                        .label("is missing " + displayName)
                        .documentedAt(userManual("validation_problems", MISSING_ANNOTATION.toLowerCase()))
                        .noLocation()
                        .category(VALIDATION, "property", TextUtil.screamingSnakeToKebabCase(MISSING_ANNOTATION))
                        .severity(ERROR)
                        .details("A property without annotation isn't considered during up-to-date checking")
                        .solution("Add " + displayName)
                        .solution("Mark it as @Internal")
                );
                continue;
            }

            PropertyAnnotationHandler annotationHandler = propertyAnnotationHandlers.get(propertyType);
            if (annotationHandler == null) {
                validationContext.visitPropertyProblem(problem ->
                    problem
                        .forProperty(propertyAnnotationMetadata.getPropertyName())
                        .label("is annotated with invalid property type @%s", propertyType.getSimpleName())
                        .documentedAt(userManual("validation_problems", ANNOTATION_INVALID_IN_CONTEXT.toLowerCase()))
                        .noLocation()
                        .category(VALIDATION, "property", TextUtil.screamingSnakeToKebabCase(ANNOTATION_INVALID_IN_CONTEXT))
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
                            .label("is annotated with @" + annotationType.getSimpleName() + " but that is not allowed for '" + propertyType.getSimpleName() + "' properties")
                            .documentedAt(userManual("validation_problems", INCOMPATIBLE_ANNOTATIONS.toLowerCase()))
                            .noLocation()
                            .category(VALIDATION, "property", TextUtil.screamingSnakeToKebabCase(INCOMPATIBLE_ANNOTATIONS))
                            .severity(ERROR)
                            .details("This modifier is used in conjunction with a property of type '" + propertyType.getSimpleName() + "' but this doesn't have semantics")
                            .solution("Remove the '@" + annotationType.getSimpleName() + "' annotation"));
                } else if (!allowedPropertyModifiers.contains(annotationType)) {
                    validationContext.visitPropertyProblem(problem ->
                        problem
                            .forProperty(propertyAnnotationMetadata.getPropertyName())
                            .label("is annotated with invalid modifier @%s", annotationType.getSimpleName())
                            .documentedAt(userManual("validation_problems", ANNOTATION_INVALID_IN_CONTEXT.toLowerCase()))
                            .noLocation()
                            .category(VALIDATION, "property", TextUtil.screamingSnakeToKebabCase(ANNOTATION_INVALID_IN_CONTEXT))
                            .severity(ERROR)
                            .details("The '@" + annotationType.getSimpleName() + "' annotation cannot be used in this context")
                            .solution("Remove the annotation")
                            .solution("Use a different annotation, e.g one of " + toListOfAnnotations(allowedPropertyModifiers))
                    );
                }
            }

            PropertyMetadata property = new DefaultPropertyMetadata(propertyType, propertyAnnotationMetadata);
            annotationHandler.validatePropertyMetadata(property, validationContext);

            if (annotationHandler.isPropertyRelevant()) {
                effectiveProperties.add(property);
            }
        }
        return new DefaultTypeMetadata(publicType, effectiveProperties.build(), validationContext, propertyAnnotationHandlers);
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
        private final ReplayingTypeValidationContext validationProblems;
        private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers;

        DefaultTypeMetadata(
            Class<?> type,
            ImmutableSet<PropertyMetadata> propertiesMetadata,
            ReplayingTypeValidationContext validationProblems,
            ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers
        ) {
            this.type = type;
            this.propertiesMetadata = propertiesMetadata;
            this.validationProblems = validationProblems;
            this.annotationHandlers = annotationHandlers;
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
        public boolean hasAnnotatedProperties() {
            return !propertiesMetadata.isEmpty();
        }

        @Override
        public PropertyAnnotationHandler getAnnotationHandlerFor(PropertyMetadata propertyMetadata) {
            return annotationHandlers.get(propertyMetadata.getPropertyType());
        }

        @Override
        public Class<?> getType() {
            return type;
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

        @Nullable
        @Override
        public <T extends Annotation> Optional<T> getAnnotation(Class<T> annotationType) {
            return annotationMetadata.getAnnotation(annotationType);
        }

        @Nullable
        @Override
        public Optional<Annotation> getAnnotationForCategory(AnnotationCategory category) {
            return Optional.ofNullable(annotationMetadata.getAnnotations().get(category));
        }

        @Override
        public boolean hasAnnotationForCategory(AnnotationCategory category) {
            return annotationMetadata.getAnnotations().get(category) != null;
        }

        @Override
        public Class<? extends Annotation> getPropertyType() {
            return propertyType;
        }

        @Override
        public TypeToken<?> getDeclaredType() {
            return annotationMetadata.getDeclaredType();
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
