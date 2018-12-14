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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.gradle.api.Transformer;
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
import org.gradle.api.internal.tasks.properties.bean.PropertyMetadata;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultWorkPropertyMetadataStore implements WorkPropertyMetadataStore {

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

    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> annotationHandlers;
    private final CrossBuildInMemoryCache<Class<?>, TypeMetadata> cache;
    private final PropertyExtractor propertyExtractor;
    private Transformer<TypeMetadata, Class<?>> typeMetadataFactory = new Transformer<TypeMetadata, Class<?>>() {
        @Override
        public TypeMetadata transform(Class<?> type) {
            return createTypeMetadata(type);
        }
    };

    public DefaultWorkPropertyMetadataStore(Iterable<? extends PropertyAnnotationHandler> customAnnotationHandlers, CrossBuildInMemoryCacheFactory cacheFactory) {
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
        this.propertyExtractor = new PropertyExtractor(((Map<Class<? extends Annotation>, PropertyAnnotationHandler>) Maps.uniqueIndex(allAnnotationHandlers, new Function<PropertyAnnotationHandler, Class<? extends Annotation>>() {
            @Override
            public Class<? extends Annotation> apply(PropertyAnnotationHandler handler) {
                return handler.getAnnotationType();
            }
        })).keySet(), relevantAnnotationTypes, annotationOverrides);
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
    public <T> TypeMetadata getTypeMetadata(final Class<T> type) {
        return cache.get(type, typeMetadataFactory);
    }

    private <T> TypeMetadata createTypeMetadata(Class<T> type) {
        Set<PropertyMetadata> extractedProperties = propertyExtractor.extractPropertyMetadata(type);
        ImmutableSet.Builder<WorkPropertyMetadata> builder = ImmutableSet.builder();
        for (PropertyMetadata property : extractedProperties) {
            builder.add(new DefaultWorkPropertyMetadata(property, annotationHandlers.get(property.getPropertyType())));
        }
        return new DefaultTypeMetadata(builder.build());
    }

    private class DefaultTypeMetadata implements TypeMetadata {
        private final ImmutableSet<WorkPropertyMetadata> propertiesMetadata;

        DefaultTypeMetadata(ImmutableSet<WorkPropertyMetadata> propertiesMetadata) {
            this.propertiesMetadata = propertiesMetadata;
        }

        @Override
        public Set<WorkPropertyMetadata> getPropertiesMetadata() {
            return propertiesMetadata;
        }

        @Override
        public boolean hasAnnotatedProperties() {
            for (WorkPropertyMetadata metadata : propertiesMetadata) {
                if (metadata.getPropertyType() != null) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class DefaultWorkPropertyMetadata implements WorkPropertyMetadata {
        private final PropertyAnnotationHandler annotationHandler;
        private final PropertyMetadata property;

        public DefaultWorkPropertyMetadata(PropertyMetadata property, PropertyAnnotationHandler annotationHandler) {
            this.property = property;
            this.annotationHandler = annotationHandler;
        }

        @Override
        public String getFieldName() {
            return property.getFieldName();
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return property.isAnnotationPresent(annotationType);
        }

        @Override
        @Nullable
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return property.getAnnotation(annotationType);
        }

        @Override
        public List<String> getValidationMessages() {
            return property.getValidationMessages();
        }

        @Override
        @Nullable
        public PropertyAnnotationHandler getPropertyValueVisitor() {
            return annotationHandler;
        }

        @Override
        @Nullable
        public Class<? extends Annotation> getPropertyType() {
            return property.getPropertyType();
        }

        @Override
        public Class<?> getDeclaredType() {
            return property.getDeclaredType();
        }

        @Override
        public Method getMethod() {
            return property.getGetterMethod();
        }
    }

}
