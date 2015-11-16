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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ModelSchemaAspectExtractor {
    private final List<ModelSchemaAspectExtractionStrategy> strategies;

    public ModelSchemaAspectExtractor() {
        this(Collections.<ModelSchemaAspectExtractionStrategy>emptyList());
    }

    public ModelSchemaAspectExtractor(Collection<ModelSchemaAspectExtractionStrategy> strategies) {
        this.strategies = ImmutableList.copyOf(strategies);
    }

    public <T> List<ModelSchemaAspect> extract(ModelSchemaExtractionContext<T> extractionContext, List<ModelPropertyExtractionResult<?>> propertyResults) {
        List<ModelSchemaAspect> aspects = Lists.newArrayList();
        for (ModelSchemaAspectExtractionStrategy strategy : strategies) {
            ModelSchemaAspectExtractionResult result = strategy.extract(extractionContext, propertyResults);
            if (result != null) {
                aspects.add(result.getAspect());
            }
        }
        return aspects;
    }
}
