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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
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

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.function.Predicate.isEqual;
import static org.gradle.api.problems.Severity.ERROR;
import static org.gradle.api.problems.internal.DefaultProblemCategory.VALIDATION;
import static org.gradle.internal.RenderingUtils.oxfordJoin;
import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.internal.properties.annotations.PropertyAnnotationHandler.Kind.OUTPUT;
import static org.gradle.internal.reflect.annotations.AnnotationCategory.TYPE;

public class DefaultTypeMetadataStore implements TypeMetadataStore {
    private final Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers;
    private final Map<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> propertyAnnotationHandlers;
    private final Set<Class<? extends Annotation>> allowedPropertyModifiers;
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
        boolean hasOutputAnnotation = annotationHandlers.stream()
            .map(PropertyAnnotationHandler::getKind)
            .anyMatch(isEqual(OUTPUT));
        return "an input " +
            (hasOutputAnnotation ? "or output " : "") +
            "annotation";
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

        validateAnnotationHandlers(annotationMetadata, publicType, validationContext);

        Builder<PropertyMetadata> effectiveProperties = ImmutableSet.builderWithExpectedSize(annotationMetadata.getPropertiesAnnotationMetadata().size());
        for (PropertyAnnotationMetadata propertyAnnotationMetadata : annotationMetadata.getPropertiesAnnotationMetadata()) {
            Map<AnnotationCategory, Annotation> propertyAnnotations = propertyAnnotationMetadata.getAnnotations();
            Class<? extends Annotation> propertyType = propertyTypeResolver.resolveAnnotationType(propertyAnnotations);
            if (propertyType == null) {
                addMissingPropertyTypeProblem(propertyAnnotationMetadata, validationContext);
                continue;
            }

            PropertyAnnotationHandler annotationHandler = propertyAnnotationHandlers.get(propertyType);
            if (annotationHandler == null) {
                addMissingAnnotationHandlerProblem(propertyAnnotationMetadata, validationContext, propertyType);
                continue;
            }

            validateAllowedModifiers(propertyAnnotationMetadata, annotationHandler, propertyAnnotations, validationContext, propertyType);

            PropertyMetadata property = new DefaultPropertyMetadata(propertyType, propertyAnnotationMetadata);
            annotationHandler.validatePropertyMetadata(property, validationContext);

            if (annotationHandler.isPropertyRelevant()) {
                effectiveProperties.add(property);
            }
        }
        return new DefaultTypeMetadata(publicType, effectiveProperties.build(), validationContext, propertyAnnotationHandlers);
    }

    private void validateAllowedModifiers(PropertyAnnotationMetadata propertyAnnotationMetadata, PropertyAnnotationHandler annotationHandler, Map<AnnotationCategory, Annotation> propertyAnnotations, ReplayingTypeValidationContext validationContext, Class<? extends Annotation> propertyType) {
        Set<Class<? extends Annotation>> allowedModifiersForPropertyType = annotationHandler.getAllowedModifiers();
        propertyAnnotations.entrySet().stream()
            .filter(entry -> entry.getKey() != TYPE)
            .map(annotationCategoryAnnotationEntry -> annotationCategoryAnnotationEntry.getValue().annotationType())
            .forEach(annotationType -> {
                if (!allowedModifiersForPropertyType.contains(annotationType)) {
                    addInvalidModifierForPropertyTypeProblem(propertyAnnotationMetadata, validationContext, annotationType, propertyType);
                } else if (!allowedPropertyModifiers.contains(annotationType)) {
                    addInvalidPropertyModifierProblem(propertyAnnotationMetadata, validationContext, annotationType);
                }
            });
    }

    private void addInvalidPropertyModifierProblem(PropertyAnnotationMetadata propertyAnnotationMetadata, ReplayingTypeValidationContext validationContext, Class<? extends Annotation> annotationType) {
        validationContext.visitPropertyProblem(problem ->
            problem
                .forProperty(propertyAnnotationMetadata.getPropertyName())
                .label("is annotated with invalid modifier @%s", annotationType.getSimpleName())
                .documentedAt(userManual("validation_problems", ANNOTATION_INVALID_IN_CONTEXT.toLowerCase()))
                .noLocation()
                .category(VALIDATION, ANNOTATION_INVALID_IN_CONTEXT)
                .severity(ERROR)
                .details("The '@" + annotationType.getSimpleName() + "' annotation cannot be used in this context")
                .solution("Remove the annotation")
                .solution("Use a different annotation, e.g one of " + toListOfAnnotations(allowedPropertyModifiers))
        );
    }

    private static void addInvalidModifierForPropertyTypeProblem(PropertyAnnotationMetadata propertyAnnotationMetadata, ReplayingTypeValidationContext validationContext, Class<? extends Annotation> annotationType, Class<? extends Annotation> propertyType) {
        validationContext.visitPropertyProblem(problem ->
            problem
                .forProperty(propertyAnnotationMetadata.getPropertyName())
                .label("is annotated with @" + annotationType.getSimpleName() + " but that is not allowed for '" + propertyType.getSimpleName() + "' properties")
                .documentedAt(userManual("validation_problems", INCOMPATIBLE_ANNOTATIONS.toLowerCase()))
                .noLocation()
                .category(VALIDATION, INCOMPATIBLE_ANNOTATIONS)
                .severity(ERROR)
                .details("This modifier is used in conjunction with a property of type '" + propertyType.getSimpleName() + "' but this doesn't have semantics")
                .solution("Remove the '@" + annotationType.getSimpleName() + "' annotation"));
    }

    private void addMissingAnnotationHandlerProblem(PropertyAnnotationMetadata propertyAnnotationMetadata, ReplayingTypeValidationContext validationContext, Class<? extends Annotation> propertyType) {
        validationContext.visitPropertyProblem(problem ->
            problem
                .forProperty(propertyAnnotationMetadata.getPropertyName())
                .label("is annotated with invalid property type @%s", propertyType.getSimpleName())
                .documentedAt(userManual("validation_problems", ANNOTATION_INVALID_IN_CONTEXT.toLowerCase()))
                .noLocation()
                .category(VALIDATION, ANNOTATION_INVALID_IN_CONTEXT)
                .severity(ERROR)
                .details("The '@" + propertyType.getSimpleName() + "' annotation cannot be used in this context")
                .solution("Remove the property")
                .solution("Use a different annotation, e.g one of " + toListOfAnnotations(propertyAnnotationHandlers.keySet()))
        );
    }

    private void addMissingPropertyTypeProblem(PropertyAnnotationMetadata propertyAnnotationMetadata, ReplayingTypeValidationContext validationContext) {
        validationContext.visitPropertyProblem(problem ->
            problem
                .forProperty(propertyAnnotationMetadata.getPropertyName())
                .label("is missing " + displayName)
                .documentedAt(userManual("validation_problems", MISSING_ANNOTATION.toLowerCase()))
                .noLocation()
                .category(VALIDATION, MISSING_ANNOTATION)
                .severity(ERROR)
                .details("A property without annotation isn't considered during up-to-date checking")
                .solution("Add " + displayName)
                .solution("Mark it as @Internal")
        );
    }

    private void validateAnnotationHandlers(TypeAnnotationMetadata annotationMetadata, Class<?> publicType, ReplayingTypeValidationContext validationContext) {
        for (TypeAnnotationHandler annotationHandler : typeAnnotationHandlers) {
            if (annotationMetadata.isAnnotationPresent(annotationHandler.getAnnotationType())) {
                annotationHandler.validateTypeMetadata(publicType, validationContext);
            }
        }
    }

    private static String toListOfAnnotations(Set<Class<? extends Annotation>> classes) {
        return classes.stream()
            .map(Class::getSimpleName)
            .map(s -> "@" + s)
            .sorted()
            .collect(oxfordJoin("or"));
    }

    private static class DefaultTypeMetadata implements TypeMetadata {
        private final Class<?> type;
        private final Set<PropertyMetadata> propertiesMetadata;
        private final ReplayingTypeValidationContext validationProblems;
        private final Map<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers;

        DefaultTypeMetadata(
            Class<?> type,
            Set<PropertyMetadata> propertiesMetadata,
            ReplayingTypeValidationContext validationProblems,
            Map<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers
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

}
