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
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.properties.annotations.DefaultTypeMetadataStore;
import org.gradle.internal.properties.annotations.NoOpPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.TypeAnnotationHandler;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.properties.bean.DefaultPropertyWalker;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class InspectionSchemeFactory {
    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> allKnownPropertyHandlers;
    private final ImmutableList<TypeAnnotationHandler> allKnownTypeHandlers;
    private final TypeAnnotationMetadataStore typeAnnotationMetadataStore;
    private final CrossBuildInMemoryCacheFactory cacheFactory;

    public InspectionSchemeFactory(
        List<? extends TypeAnnotationHandler> allKnownTypeHandlers,
        List<? extends PropertyAnnotationHandler> allKnownPropertyHandlers,
        TypeAnnotationMetadataStore typeAnnotationMetadataStore,
        CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        ImmutableMap.Builder<Class<? extends Annotation>, PropertyAnnotationHandler> builder = ImmutableMap.builder();
        for (PropertyAnnotationHandler handler : allKnownPropertyHandlers) {
            builder.put(handler.getAnnotationType(), handler);
        }
        this.allKnownTypeHandlers = ImmutableList.copyOf(allKnownTypeHandlers);
        this.allKnownPropertyHandlers = builder.build();
        this.typeAnnotationMetadataStore = typeAnnotationMetadataStore;
        this.cacheFactory = cacheFactory;
    }

    /**
     * Creates a new {@link InspectionScheme} with the given annotations enabled and using the given instantiation scheme.
     */
    public InspectionScheme inspectionScheme(Collection<Class<? extends Annotation>> annotations, Collection<Class<? extends Annotation>> propertyModifiers, InstantiationScheme instantiationScheme) {
        ImmutableList.Builder<PropertyAnnotationHandler> propertyHandlers = ImmutableList.builderWithExpectedSize(annotations.size());
        for (Class<? extends Annotation> annotation : annotations) {
            PropertyAnnotationHandler propertyHandler = allKnownPropertyHandlers.get(annotation);
            if (propertyHandler == null) {
                throw new IllegalArgumentException(String.format("@%s is not a registered property type annotation.", annotation.getSimpleName()));
            }
            propertyHandlers.add(propertyHandler);
        }
        for (Class<? extends Annotation> annotation : instantiationScheme.getInjectionAnnotations()) {
            if (!annotations.contains(annotation)) {
                propertyHandlers.add(new NoOpPropertyAnnotationHandler(annotation));
            }
        }
        return new InspectionSchemeImpl(allKnownTypeHandlers, propertyHandlers.build(), propertyModifiers, typeAnnotationMetadataStore, cacheFactory);
    }

    private static class InspectionSchemeImpl implements InspectionScheme {
        private final DefaultPropertyWalker propertyWalker;
        private final DefaultTypeMetadataStore metadataStore;

        public InspectionSchemeImpl(List<TypeAnnotationHandler> typeHandlers, List<PropertyAnnotationHandler> propertyHandlers, Collection<Class<? extends Annotation>> propertyModifiers, TypeAnnotationMetadataStore typeAnnotationMetadataStore, CrossBuildInMemoryCacheFactory cacheFactory) {
            DefaultPropertyTypeResolver propertyTypeResolver = new DefaultPropertyTypeResolver();
            metadataStore = new DefaultTypeMetadataStore(typeHandlers, propertyHandlers, propertyModifiers, typeAnnotationMetadataStore, propertyTypeResolver, cacheFactory);
            propertyWalker = new DefaultPropertyWalker(metadataStore, new ScriptSourceAwareImplementationResolver(), propertyHandlers);
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
