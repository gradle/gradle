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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.Transformer;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.tasks.properties.annotations.AbstractOutputPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.TypeAnnotationHandler;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.reflect.TypeValidationContext;
import org.gradle.internal.reflect.TypeValidationContext.ReplayingTypeValidationContext;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.NORMALIZATION;
import static org.gradle.internal.reflect.AnnotationCategory.TYPE;
import static org.gradle.internal.reflect.TypeValidationContext.Severity.ERROR;
import static org.gradle.internal.reflect.TypeValidationContext.Severity.WARNING;

public class DefaultTypeMetadataStore implements TypeMetadataStore {
    private final Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers;
    private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> propertyAnnotationHandlers;
    private final ImmutableSet<Class<? extends Annotation>> allowedPropertyModifiers;
    private final CrossBuildInMemoryCache<Class<?>, TypeMetadata> cache;
    private final TypeAnnotationMetadataStore typeAnnotationMetadataStore;
    private final String displayName;
    private final Transformer<TypeMetadata, Class<?>> typeMetadataFactory = this::createTypeMetadata;

    public DefaultTypeMetadataStore(
        Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers,
        Collection<? extends PropertyAnnotationHandler> propertyAnnotationHandlers,
        Collection<Class<? extends Annotation>> allowedPropertyModifiers,
        TypeAnnotationMetadataStore typeAnnotationMetadataStore,
        CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        this.typeAnnotationHandlers = ImmutableSet.copyOf(typeAnnotationHandlers);
        this.propertyAnnotationHandlers = Maps.uniqueIndex(propertyAnnotationHandlers, PropertyAnnotationHandler::getAnnotationType);
        this.allowedPropertyModifiers = ImmutableSet.copyOf(allowedPropertyModifiers);
        this.typeAnnotationMetadataStore = typeAnnotationMetadataStore;
        this.displayName = calculateDisplayName(propertyAnnotationHandlers);
        this.cache = cacheFactory.newClassCache();
    }

    private static String calculateDisplayName(Iterable<? extends PropertyAnnotationHandler> annotationHandlers) {
        for (PropertyAnnotationHandler annotationHandler : annotationHandlers) {
            if (annotationHandler instanceof AbstractOutputPropertyAnnotationHandler) {
                return "an input or output annotation";
            }
        }
        return "an input annotation";
    }

    @Override
    public <T> TypeMetadata getTypeMetadata(Class<T> type) {
        return cache.get(type, typeMetadataFactory);
    }

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
            Annotation typeAnnotation = propertyAnnotations.get(TYPE);
            Annotation normalizationAnnotation = propertyAnnotations.get(NORMALIZATION);
            Class<? extends Annotation> propertyType = determinePropertyType(typeAnnotation, normalizationAnnotation);
            if (propertyType == null) {
                validationContext.visitPropertyProblem(WARNING,
                    propertyAnnotationMetadata.getPropertyName(),
                    String.format("is not annotated with %s", displayName)
                );
                continue;
            }

            PropertyAnnotationHandler annotationHandler = propertyAnnotationHandlers.get(propertyType);
            if (annotationHandler == null) {
                validationContext.visitPropertyProblem(ERROR,
                    propertyAnnotationMetadata.getPropertyName(),
                    String.format("is annotated with invalid property type @%s",
                        propertyType.getSimpleName())
                );
                continue;
            }

            ImmutableSet<? extends AnnotationCategory> allowedModifiersForPropertyType = annotationHandler.getAllowedModifiers();
            for (Map.Entry<AnnotationCategory, Annotation> entry : propertyAnnotations.entrySet()) {
                AnnotationCategory annotationCategory = entry.getKey();
                if (annotationCategory == TYPE) {
                    continue;
                }
                Class<? extends Annotation> annotationType = entry.getValue().annotationType();
                if (!allowedModifiersForPropertyType.contains(annotationCategory)) {
                    validationContext.visitPropertyProblem(WARNING,
                        propertyAnnotationMetadata.getPropertyName(),
                        String.format("is annotated with @%s that is not allowed for @%s properties",
                            annotationType.getSimpleName(), propertyType.getSimpleName())
                    );
                } else if (!allowedPropertyModifiers.contains(annotationType)) {
                    validationContext.visitPropertyProblem(ERROR,
                        propertyAnnotationMetadata.getPropertyName(),
                        String.format("has invalid annotation @%s",
                            annotationType.getSimpleName())
                    );
                }
            }

            PropertyMetadata property = new DefaultPropertyMetadata(propertyType, propertyAnnotationMetadata);
            annotationHandler.validatePropertyMetadata(property, validationContext);

            if (annotationHandler.isPropertyRelevant()) {
                effectiveProperties.add(property);
            }
        }
        return new DefaultTypeMetadata(effectiveProperties.build(), validationContext, propertyAnnotationHandlers);
    }

    @Nullable
    private Class<? extends Annotation> determinePropertyType(@Nullable Annotation typeAnnotation, @Nullable Annotation normalizationAnnotation) {
        if (typeAnnotation != null) {
            return typeAnnotation.annotationType();
        } else if (normalizationAnnotation != null) {
            if (normalizationAnnotation.annotationType().equals(Classpath.class)
            || normalizationAnnotation.annotationType().equals(CompileClasspath.class)) {
                return InputFiles.class;
            }
        }
        return null;
    }

    private static class DefaultTypeMetadata implements TypeMetadata {
        private final ImmutableSet<PropertyMetadata> propertiesMetadata;
        private final ReplayingTypeValidationContext validationProblems;
        private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers;

        DefaultTypeMetadata(
            ImmutableSet<PropertyMetadata> propertiesMetadata,
            ReplayingTypeValidationContext validationProblems,
            ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers
        ) {
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
        public Annotation getAnnotationForCategory(AnnotationCategory category) {
            return annotationMetadata.getAnnotations().get(category);
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
        public Method getGetterMethod() {
            return annotationMetadata.getMethod();
        }

        @Override
        public String toString() {
            return String.format("@%s %s", propertyType.getSimpleName(), getPropertyName());
        }
    }
}
