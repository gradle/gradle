/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.base.Functions;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public class TestUnmanagedTypeWithManagedSuperTypeExtractionStrategy extends ManagedImplStructSchemaExtractionStrategySupport {
    private final Class<?> delegateType;

    public TestUnmanagedTypeWithManagedSuperTypeExtractionStrategy(Class<?> delegateType) {
        this(delegateType, new ModelSchemaAspectExtractor());
    }

    public TestUnmanagedTypeWithManagedSuperTypeExtractionStrategy(Class<?> delegateType, ModelSchemaAspectExtractor aspectExtractor) {
        super(aspectExtractor);
        this.delegateType = delegateType;
    }

    @Override
    protected <R> ModelSchema<R> createSchema(ModelSchemaExtractionContext<R> extractionContext, ModelSchemaStore store, ModelType<R> type, List<ModelProperty<?>> properties, List<ModelSchemaAspect> aspects) {
        return ModelSchema.struct(type, properties, aspects, type.getConcreteClass(), delegateType, Functions.<NodeInitializer>constant(null));
    }
}

