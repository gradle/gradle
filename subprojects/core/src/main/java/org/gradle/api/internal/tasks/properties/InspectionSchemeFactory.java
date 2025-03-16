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
import org.gradle.internal.properties.annotations.FunctionAnnotationHandler;
import org.gradle.internal.properties.annotations.NoOpPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.MissingPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.TypeAnnotationHandler;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.properties.bean.DefaultPropertyWalker;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@ServiceScope(Scope.Global.class)
public class InspectionSchemeFactory {
    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> allKnownPropertyHandlers;
    private final Map<Class<? extends Annotation>, FunctionAnnotationHandler> allKnownFunctionHandlers;
    private final ImmutableList<TypeAnnotationHandler> allKnownTypeHandlers;
    private final TypeAnnotationMetadataStore typeAnnotationMetadataStore;
    private final CrossBuildInMemoryCacheFactory cacheFactory;

    public InspectionSchemeFactory(
        List<? extends TypeAnnotationHandler> allKnownTypeHandlers,
        List<? extends PropertyAnnotationHandler> allKnownPropertyHandlers,
        List<? extends FunctionAnnotationHandler> allKnownFunctionHandlers,
        TypeAnnotationMetadataStore typeAnnotationMetadataStore,
        CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        ImmutableMap.Builder<Class<? extends Annotation>, PropertyAnnotationHandler> propertyHandlerBuilder = ImmutableMap.builder();
        for (PropertyAnnotationHandler handler : allKnownPropertyHandlers) {
            propertyHandlerBuilder.put(handler.getAnnotationType(), handler);
        }
        ImmutableMap.Builder<Class<? extends Annotation>, FunctionAnnotationHandler> functionHandlerBuilder = ImmutableMap.builder();
        for (FunctionAnnotationHandler handler : allKnownFunctionHandlers) {
            functionHandlerBuilder.put(handler.getAnnotationType(), handler);
        }
        this.allKnownTypeHandlers = ImmutableList.copyOf(allKnownTypeHandlers);
        this.allKnownPropertyHandlers = propertyHandlerBuilder.build();
        this.allKnownFunctionHandlers = functionHandlerBuilder.build();
        this.typeAnnotationMetadataStore = typeAnnotationMetadataStore;
        this.cacheFactory = cacheFactory;
    }


    /**
     * Creates a new {@link InspectionScheme} with the given annotations enabled and using the given instantiation scheme.  Assumes missing annotations
     * should be handled as missing inputs or outputs.
     */
    public InspectionScheme inspectionScheme(Collection<Class<? extends Annotation>> annotations, Collection<Class<? extends Annotation>> propertyModifiers, Collection<Class<? extends Annotation>> methodModifiers, InstantiationScheme instantiationScheme) {
        return inspectionScheme(annotations, propertyModifiers, methodModifiers, instantiationScheme, MissingPropertyAnnotationHandler.MISSING_INPUT_OUTPUT_HANDLER);
    }

    /**
     * Creates a new {@link InspectionScheme} with the given annotations enabled and using the given instantiation scheme.  Uses the provided missing
     * annotation handler to determine how to handle missing annotations.
     */
    public InspectionScheme inspectionScheme(Collection<Class<? extends Annotation>> annotations, Collection<Class<? extends Annotation>> propertyModifiers, Collection<Class<? extends Annotation>> methodModifiers, InstantiationScheme instantiationScheme, MissingPropertyAnnotationHandler missingAnnotationProblemHandler) {
        ImmutableList.Builder<PropertyAnnotationHandler> propertyHandlers = ImmutableList.builderWithExpectedSize(annotations.size());
        ImmutableList.Builder<FunctionAnnotationHandler> functionHandlers = ImmutableList.builderWithExpectedSize(annotations.size());
        for (Class<? extends Annotation> annotation : annotations) {
            PropertyAnnotationHandler propertyHandler = allKnownPropertyHandlers.get(annotation);
            FunctionAnnotationHandler methodHandler = allKnownFunctionHandlers.get(annotation);
            if (propertyHandler == null && methodHandler == null) {
                throw new IllegalArgumentException(String.format("@%s is not a registered property or method type annotation.", annotation.getSimpleName()));
            }
            if (propertyHandler != null) {
                propertyHandlers.add(propertyHandler);
            }
            if (methodHandler != null) {
                functionHandlers.add(methodHandler);
            }
        }

        for (Class<? extends Annotation> annotation : instantiationScheme.getInjectionAnnotations()) {
            if (!annotations.contains(annotation)) {
                propertyHandlers.add(new NoOpPropertyAnnotationHandler(annotation));
            }
        }
        return new InspectionSchemeImpl(allKnownTypeHandlers, propertyHandlers.build(), propertyModifiers, functionHandlers.build(), methodModifiers, typeAnnotationMetadataStore, cacheFactory, missingAnnotationProblemHandler);
    }

    private static class InspectionSchemeImpl implements InspectionScheme {
        private final DefaultPropertyWalker propertyWalker;
        private final DefaultTypeMetadataStore metadataStore;

        public InspectionSchemeImpl(List<TypeAnnotationHandler> typeHandlers, List<PropertyAnnotationHandler> propertyHandlers, Collection<Class<? extends Annotation>> propertyModifiers, List<FunctionAnnotationHandler> methodHandlers, Collection<Class<? extends Annotation>> methodModifiers, TypeAnnotationMetadataStore typeAnnotationMetadataStore, CrossBuildInMemoryCacheFactory cacheFactory, MissingPropertyAnnotationHandler missingAnnotationProblemHandler) {
            DefaultPropertyTypeResolver propertyTypeResolver = new DefaultPropertyTypeResolver();
            metadataStore = new DefaultTypeMetadataStore(typeHandlers, propertyHandlers, propertyModifiers, methodHandlers, methodModifiers, typeAnnotationMetadataStore, propertyTypeResolver, cacheFactory, missingAnnotationProblemHandler);
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
