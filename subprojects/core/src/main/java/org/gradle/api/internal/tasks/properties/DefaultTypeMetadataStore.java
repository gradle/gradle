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
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import groovy.lang.GroovyObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.tasks.properties.annotations.AbstractOutputPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OverridingPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.TypeAnnotationHandler;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.internal.reflect.PropertyExtractor;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.reflect.ValidationProblem;
import org.gradle.internal.scripts.ScriptOrigin;
import org.gradle.work.Incremental;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Set;

public class DefaultTypeMetadataStore implements TypeMetadataStore {
    // Avoid reflecting on classes we know we don't need to look at
    private static final ImmutableSet<Class<?>> IGNORED_SUPER_CLASSES = ImmutableSet.of(
            ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class, IConventionAware.class, ExtensionAware.class, HasConvention.class, ScriptOrigin.class, DynamicObjectAware.class
    );

    private static final ImmutableSet<Class<?>> IGNORED_METHODS = ImmutableSet.of(Object.class, GroovyObject.class, ScriptOrigin.class);

    private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers;
    private final Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers;
    private final CrossBuildInMemoryCache<Class<?>, TypeMetadata> cache;
    private final PropertyExtractor propertyExtractor;
    private Transformer<TypeMetadata, Class<?>> typeMetadataFactory = new Transformer<TypeMetadata, Class<?>>() {
        @Override
        public TypeMetadata transform(Class<?> type) {
            return createTypeMetadata(type);
        }
    };

    public DefaultTypeMetadataStore(Collection<? extends PropertyAnnotationHandler> annotationHandlers, Set<Class<? extends Annotation>> otherKnownAnnotations, Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers, CrossBuildInMemoryCacheFactory cacheFactory) {
        this.annotationHandlers = Maps.uniqueIndex(annotationHandlers, new Function<PropertyAnnotationHandler, Class<? extends Annotation>>() {
            @Override
            public Class<? extends Annotation> apply(PropertyAnnotationHandler handler) {
                return handler.getAnnotationType();
            }
        });
        this.typeAnnotationHandlers = typeAnnotationHandlers;
        Multimap<Class<? extends Annotation>, Class<? extends Annotation>> annotationOverrides = collectAnnotationOverrides(annotationHandlers);
        Set<Class<? extends Annotation>> relevantAnnotationTypes = collectRelevantAnnotationTypes(Collections2.transform(annotationHandlers, new Function<PropertyAnnotationHandler, Class<? extends Annotation>>() {
            @Override
            public Class<? extends Annotation> apply(PropertyAnnotationHandler handler) {
                return handler.getAnnotationType();
            }
        }));
        String displayName = calculateDisplayName(annotationHandlers);
        this.propertyExtractor = new PropertyExtractor(displayName, this.annotationHandlers.keySet(), relevantAnnotationTypes, annotationOverrides, otherKnownAnnotations, IGNORED_SUPER_CLASSES, IGNORED_METHODS);
        this.cache = cacheFactory.newClassCache();
    }

    private String calculateDisplayName(Iterable<? extends PropertyAnnotationHandler> annotationHandlers) {
        for (PropertyAnnotationHandler annotationHandler : annotationHandlers) {
            if (annotationHandler instanceof AbstractOutputPropertyAnnotationHandler) {
                return "an input or output annotation";
            }
        }
        return "an input annotation";
    }

    private static Multimap<Class<? extends Annotation>, Class<? extends Annotation>> collectAnnotationOverrides(Iterable<? extends PropertyAnnotationHandler> allAnnotationHandlers) {
        ImmutableSetMultimap.Builder<Class<? extends Annotation>, Class<? extends Annotation>> builder = ImmutableSetMultimap.builder();
        for (PropertyAnnotationHandler handler : allAnnotationHandlers) {
            if (handler instanceof OverridingPropertyAnnotationHandler) {
                for (Class<? extends Annotation> overriddenAnnotationType : ((OverridingPropertyAnnotationHandler) handler).getOverriddenAnnotationTypes()) {
                    builder.put(overriddenAnnotationType, handler.getAnnotationType());
                }
            }
        }
        return builder.build();
    }

    private static Set<Class<? extends Annotation>> collectRelevantAnnotationTypes(Collection<Class<? extends Annotation>> propertyTypeAnnotations) {
        return ImmutableSet.<Class<? extends Annotation>>builder()
                .addAll(propertyTypeAnnotations)
                .add(Optional.class)
                .add(SkipWhenEmpty.class)
                .add(PathSensitive.class)
                .add(Incremental.class)
                .build();
    }

    @Override
    public <T> TypeMetadata getTypeMetadata(final Class<T> type) {
        return cache.get(type, typeMetadataFactory);
    }

    private <T> TypeMetadata createTypeMetadata(Class<T> type) {
        Class<?> publicType = GeneratedSubclasses.unpack(type);
        RecordingValidationContext validationContext = new RecordingValidationContext();
        for (TypeAnnotationHandler annotationHandler : typeAnnotationHandlers) {
            if (publicType.isAnnotationPresent(annotationHandler.getAnnotationType())) {
                annotationHandler.validateTypeMetadata(publicType, validationContext);
            }
        }
        ImmutableSet<PropertyMetadata> properties = propertyExtractor.extractPropertyMetadata(publicType, validationContext);
        ImmutableSet.Builder<PropertyMetadata> effectiveProperties = ImmutableSet.builderWithExpectedSize(properties.size());
        for (PropertyMetadata property : properties) {
            PropertyAnnotationHandler annotationHandler = annotationHandlers.get(property.getPropertyType());
            annotationHandler.validatePropertyMetadata(property, validationContext);
            if (annotationHandler.isPropertyRelevant()) {
                effectiveProperties.add(property);
            }
        }
        return new DefaultTypeMetadata(effectiveProperties.build(), validationContext.getProblems(), annotationHandlers);
    }

    private static class RecordingValidationContext implements ParameterValidationContext {
        private ImmutableList.Builder<ValidationProblem> builder = ImmutableList.builder();

        ImmutableList<ValidationProblem> getProblems() {
            return builder.build();
        }

        @Override
        public void visitError(@Nullable String ownerPath, final String propertyName, final String message) {
            builder.add(new ValidationProblem() {
                @Override
                public void collect(@Nullable String ownerPropertyPath, ParameterValidationContext validationContext) {
                    validationContext.visitError(ownerPropertyPath, propertyName, message);
                }
            });
        }

        @Override
        public void visitError(final String message) {
            builder.add(new ValidationProblem() {
                @Override
                public void collect(@Nullable String ownerPropertyPath, ParameterValidationContext validationContext) {
                    validationContext.visitError(message);
                }
            });
        }

        @Override
        public void visitErrorStrict(@Nullable final String ownerPath, final String propertyName, final String message) {
            builder.add(new ValidationProblem() {
                @Override
                public void collect(@Nullable String ownerPropertyPath, ParameterValidationContext validationContext) {
                    validationContext.visitErrorStrict(ownerPath, propertyName, message);
                }
            });
        }

        @Override
        public void visitErrorStrict(final String message) {
            builder.add(new ValidationProblem() {
                @Override
                public void collect(@Nullable String ownerPropertyPath, ParameterValidationContext validationContext) {
                    validationContext.visitErrorStrict(message);
                }
            });
        }
    }

    private static class DefaultTypeMetadata implements TypeMetadata {
        private final ImmutableSet<PropertyMetadata> propertiesMetadata;
        private final ImmutableList<ValidationProblem> validationProblems;
        private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers;

        DefaultTypeMetadata(ImmutableSet<PropertyMetadata> propertiesMetadata, ImmutableList<ValidationProblem> validationProblems, ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers) {
            this.propertiesMetadata = propertiesMetadata;
            this.validationProblems = validationProblems;
            this.annotationHandlers = annotationHandlers;
        }

        @Override
        public void collectValidationFailures(@Nullable String ownerPropertyPath, ParameterValidationContext validationContext) {
            for (ValidationProblem problem : validationProblems) {
                problem.collect(ownerPropertyPath, validationContext);
            }
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
}
