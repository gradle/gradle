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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import groovy.lang.GroovyObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.tasks.properties.annotations.DestroysPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputFilePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.LocalStatePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.NestedBeanAnnotationHandler;
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
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.PropertyExtractor;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.scripts.ScriptOrigin;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultTypePropertyMetadataStore implements TypePropertyMetadataStore {

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
        new NestedBeanAnnotationHandler(),
        new NoOpPropertyAnnotationHandler(Inject.class),
        new NoOpPropertyAnnotationHandler(Console.class),
        new NoOpPropertyAnnotationHandler(Internal.class),
        new NoOpPropertyAnnotationHandler(OptionValues.class)
    );

    // Avoid reflecting on classes we know we don't need to look at
    private static final ImmutableSet<Class<?>> IGNORED_SUPER_CLASSES = ImmutableSet.of(
        ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class, IConventionAware.class, ExtensionAware.class, HasConvention.class, ScriptOrigin.class, DynamicObjectAware.class
    );

    private static final ImmutableSet<Class<?>> IGNORED_METHODS = ImmutableSet.of(Object.class, GroovyObject.class, ScriptOrigin.class);

    private final ImmutableMap<Class<? extends Annotation>, PropertyAnnotationHandler> annotationHandlers;
    private final CrossBuildInMemoryCache<Class<?>, TypePropertyMetadata> cache;
    private final PropertyExtractor propertyExtractor;
    private Transformer<TypePropertyMetadata, Class<?>> typeMetadataFactory = new Transformer<TypePropertyMetadata, Class<?>>() {
        @Override
        public TypePropertyMetadata transform(Class<?> type) {
            return createTypePropertyMetadata(type);
        }
    };

    public DefaultTypePropertyMetadataStore(Iterable<? extends PropertyAnnotationHandler> customAnnotationHandlers, CrossBuildInMemoryCacheFactory cacheFactory) {
        Iterable<PropertyAnnotationHandler> allAnnotationHandlers = Iterables.concat(HANDLERS, customAnnotationHandlers);
        this.annotationHandlers = Maps.uniqueIndex(allAnnotationHandlers, new Function<PropertyAnnotationHandler, Class<? extends Annotation>>() {
            @Override
            public Class<? extends Annotation> apply(PropertyAnnotationHandler handler) {
                return handler.getAnnotationType();
            }
        });
        Multimap<Class<? extends Annotation>, Class<? extends Annotation>> annotationOverrides = collectAnnotationOverrides(allAnnotationHandlers);
        Set<Class<? extends Annotation>> relevantAnnotationTypes = collectRelevantAnnotationTypes(((Map<Class<? extends Annotation>, PropertyAnnotationHandler>) Maps.uniqueIndex(allAnnotationHandlers, new Function<PropertyAnnotationHandler, Class<? extends Annotation>>() {
            @Override
            public Class<? extends Annotation> apply(PropertyAnnotationHandler handler) {
                return handler.getAnnotationType();
            }
        })).keySet());
        this.propertyExtractor = new PropertyExtractor(annotationHandlers.keySet(), relevantAnnotationTypes, annotationOverrides, IGNORED_SUPER_CLASSES, IGNORED_METHODS);
        this.cache = cacheFactory.newClassCache();
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
    public <T> TypePropertyMetadata getTypePropertyMetadata(final Class<T> type) {
        return cache.get(type, typeMetadataFactory);
    }

    private <T> TypePropertyMetadata createTypePropertyMetadata(Class<T> type) {
        return new DefaultTypePropertyMetadata(propertyExtractor.extractPropertyMetadata(type), annotationHandlers);
    }

    private static class DefaultTypePropertyMetadata implements TypePropertyMetadata {
        private final ImmutableSet<PropertyMetadata> propertiesMetadata;
        private final ImmutableMap<Class<? extends Annotation>, PropertyAnnotationHandler> annotationHandlers;

        DefaultTypePropertyMetadata(ImmutableSet<PropertyMetadata> propertiesMetadata, ImmutableMap<Class<? extends Annotation>, PropertyAnnotationHandler> annotationHandlers) {
            this.propertiesMetadata = propertiesMetadata;
            this.annotationHandlers = annotationHandlers;
        }

        @Override
        public Set<PropertyMetadata> getPropertiesMetadata() {
            return propertiesMetadata;
        }

        @Override
        public boolean hasAnnotatedProperties() {
            for (PropertyMetadata metadata : propertiesMetadata) {
                if (metadata.getPropertyType() != null) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public PropertyAnnotationHandler getAnnotationHandlerFor(PropertyMetadata propertyMetadata) {
            return annotationHandlers.get(propertyMetadata.getPropertyType());
        }
    }
}
