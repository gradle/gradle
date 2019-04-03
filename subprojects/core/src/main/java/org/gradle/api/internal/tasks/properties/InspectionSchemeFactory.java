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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.properties.annotations.NoOpPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.TypeAnnotationHandler;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.instantiation.InstantiationScheme;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InspectionSchemeFactory {
    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> allKnownPropertyHandlers;
    private final ImmutableList<TypeAnnotationHandler> allKnownTypeHandlers;
    private final CrossBuildInMemoryCacheFactory cacheFactory;

    public InspectionSchemeFactory(List<? extends PropertyAnnotationHandler> allKnownPropertyHandlers, List<? extends TypeAnnotationHandler> allKnownTypeHandlers, CrossBuildInMemoryCacheFactory cacheFactory) {
        ImmutableMap.Builder<Class<? extends Annotation>, PropertyAnnotationHandler> builder = ImmutableMap.builder();
        for (PropertyAnnotationHandler handler : allKnownPropertyHandlers) {
            builder.put(handler.getAnnotationType(), handler);
        }
        this.allKnownPropertyHandlers = builder.build();
        this.allKnownTypeHandlers = ImmutableList.copyOf(allKnownTypeHandlers);
        this.cacheFactory = cacheFactory;
    }

    /**
     * Creates a new {@link InspectionScheme} with the given annotations enabled and using the given instantiation scheme.
     */
    public InspectionScheme inspectionScheme(Collection<Class<? extends Annotation>> annotations, InstantiationScheme instantiationScheme) {
        ImmutableList.Builder<PropertyAnnotationHandler> builder = ImmutableList.builderWithExpectedSize(annotations.size());
        for (Class<? extends Annotation> annotation : annotations) {
            PropertyAnnotationHandler handler = allKnownPropertyHandlers.get(annotation);
            if (handler == null) {
                throw new IllegalArgumentException(String.format("Annotation @%s is not a registered annotation.", annotation.getSimpleName()));
            }
            builder.add(handler);
        }
        for (Class<? extends Annotation> annotation : instantiationScheme.getInjectionAnnotations()) {
            if (!annotations.contains(annotation)) {
                builder.add(new NoOpPropertyAnnotationHandler(annotation));
            }
        }
        ImmutableList<PropertyAnnotationHandler> annotationHandlers = builder.build();
        ImmutableSet.Builder<Class<? extends Annotation>> otherAnnotations = ImmutableSet.builderWithExpectedSize(allKnownPropertyHandlers.size() - annotations.size());
        for (Class<? extends Annotation> annotation : allKnownPropertyHandlers.keySet()) {
            if (!annotations.contains(annotation)) {
                otherAnnotations.add(annotation);
            }
        }
        return new InspectionSchemeImpl(annotationHandlers, otherAnnotations.build(), allKnownTypeHandlers, cacheFactory);
    }

    private static class InspectionSchemeImpl implements InspectionScheme {
        private final DefaultPropertyWalker propertyWalker;
        private final DefaultTypeMetadataStore metadataStore;

        public InspectionSchemeImpl(List<PropertyAnnotationHandler> annotationHandlers, Set<Class<? extends Annotation>> otherKnownAnnotations, List<TypeAnnotationHandler> typeHandlers, CrossBuildInMemoryCacheFactory cacheFactory) {
            metadataStore = new DefaultTypeMetadataStore(annotationHandlers, otherKnownAnnotations, typeHandlers, cacheFactory);
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
