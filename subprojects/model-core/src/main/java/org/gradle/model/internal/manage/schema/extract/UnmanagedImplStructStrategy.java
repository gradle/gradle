/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class UnmanagedImplStructStrategy extends StructSchemaExtractionStrategySupport {

    public UnmanagedImplStructStrategy(ModelSchemaAspectExtractor aspectExtractor) {
        super(aspectExtractor);
    }

    protected <R> ModelSchema<R> createSchema(final ModelSchemaExtractionContext<R> extractionContext, final ModelSchemaStore store, ModelType<R> type, List<ModelProperty<?>> properties, List<ModelSchemaAspect> aspects) {
        return new ModelUnmanagedImplStructSchema<R>(type, properties, aspects);
    }

    @Override
    protected boolean isTarget(ModelType<?> type) {
        // Everything is an unmanaged struct that hasn't been handled before
        return true;
    }

    @Override
    protected <R> void validateTypeHierarchy(ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type) {
    }

    @Override
    protected void handleOverloadedMethods(ModelSchemaExtractionContext<?> extractionContext, Collection<Method> overloadedMethods) {
    }

    @Override
    protected void handleInvalidGetter(ModelSchemaExtractionContext<?> extractionContext, PropertyAccessorExtractionContext getter, String message) {
    }

    @Override
    protected void validateSetter(ModelSchemaExtractionContext<?> extractionContext, ModelType<?> propertyType, PropertyAccessorExtractionContext getterContext, PropertyAccessorExtractionContext setterContext) {
    }

    @Override
    protected void validateAllNecessaryMethodsHandled(ModelSchemaExtractionContext<?> extractionContext, Collection<Method> allMethods, Set<Method> handledMethods) {
    }

    @Override
    protected <P> Action<ModelSchemaExtractionContext<P>> createPropertyValidator(ModelProperty<P> property, ModelSchemaCache modelSchemaCache) {
        return Actions.doNothing();
    }
}
