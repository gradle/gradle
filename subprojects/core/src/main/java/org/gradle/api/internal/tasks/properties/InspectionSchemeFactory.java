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

package org.gradle.api.internal.tasks.properties;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.UncheckedException;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InspectionSchemeFactory {
    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> allKnownHandlers;
    private final CrossBuildInMemoryCacheFactory cacheFactory;
    // Assume for now that the annotations are all part of Gradle core and are never unloaded, so use strong references to the annotation types
    private final LoadingCache<Set<Class<? extends Annotation>>, InspectionScheme> schemes = CacheBuilder.newBuilder().build(new CacheLoader<Set<Class<? extends Annotation>>, InspectionScheme>() {
        @Override
        public InspectionScheme load(Set<Class<? extends Annotation>> annotations) {
            ImmutableList.Builder<PropertyAnnotationHandler> builder = ImmutableList.builderWithExpectedSize(annotations.size());
            for (Class<? extends Annotation> annotation : annotations) {
                PropertyAnnotationHandler handler = allKnownHandlers.get(annotation);
                if (handler == null) {
                    throw new IllegalArgumentException(String.format("Annotation @%s is not a registered annotation.", annotation.getSimpleName()));
                }
                builder.add(handler);
            }
            ImmutableList<PropertyAnnotationHandler> annotationHandlers = builder.build();
            ImmutableSet.Builder<Class<? extends Annotation>> otherAnnotations = ImmutableSet.builderWithExpectedSize(allKnownHandlers.size() - annotations.size());
            for (Class<? extends Annotation> annotation : allKnownHandlers.keySet()) {
                if (!annotations.contains(annotation)) {
                    otherAnnotations.add(annotation);
                }
            }
            return new InspectionSchemeImpl(annotationHandlers, otherAnnotations.build(), cacheFactory);
        }
    });

    public InspectionSchemeFactory(List<? extends PropertyAnnotationHandler> allKnownHandlers, CrossBuildInMemoryCacheFactory cacheFactory) {
        ImmutableMap.Builder<Class<? extends Annotation>, PropertyAnnotationHandler> builder = ImmutableMap.builder();
        for (PropertyAnnotationHandler handler : allKnownHandlers) {
            builder.put(handler.getAnnotationType(), handler);
        }
        this.allKnownHandlers = builder.build();
        this.cacheFactory = cacheFactory;
    }

    /**
     * Creates an {@link InspectionScheme} with the given annotations enabled.
     */
    public InspectionScheme inspectionScheme(Collection<Class<? extends Annotation>> annotations) {
        try {
            return schemes.getUnchecked(ImmutableSet.copyOf(annotations));
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    private static class InspectionSchemeImpl implements InspectionScheme {
        private final DefaultPropertyWalker propertyWalker;
        private final DefaultTypeMetadataStore metadataStore;

        public InspectionSchemeImpl(List<PropertyAnnotationHandler> annotationHandlers, Set<Class<? extends Annotation>> otherKnownAnnotations, CrossBuildInMemoryCacheFactory cacheFactory) {
            metadataStore = new DefaultTypeMetadataStore(annotationHandlers, otherKnownAnnotations, cacheFactory);
            propertyWalker = new DefaultPropertyWalker(metadataStore);
        }

        @Override
        public TypeMetadataStore getMetadataStore() {
            return metadataStore;
        }

        @Override
        public PropertyWalker getPropertyWalker() {
            return propertyWalker;
        }
    }
}
