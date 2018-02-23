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

import org.gradle.model.RuleSource;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.RuleSourceSchema;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public class RuleSourceSchemaExtractionStrategy extends StructSchemaExtractionStrategySupport {

    private static final ModelType<RuleSource> RULE_SOURCE_MODEL_TYPE = ModelType.of(RuleSource.class);

    public RuleSourceSchemaExtractionStrategy(ModelSchemaAspectExtractor aspectExtractor) {
        super(aspectExtractor);
    }

    @Override
    protected boolean isTarget(ModelType<?> type) {
        return RULE_SOURCE_MODEL_TYPE.isAssignableFrom(type);
    }

    @Override
    protected <R> ModelSchema<R> createSchema(ModelSchemaExtractionContext<R> extractionContext, Iterable<ModelProperty<?>> properties, Set<WeaklyTypeReferencingMethod<?, ?>> nonPropertyMethods, Iterable<ModelSchemaAspect> aspects) {
        return new RuleSourceSchema<R>(extractionContext.getType(), properties, nonPropertyMethods, aspects);
    }
}
