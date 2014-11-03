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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.util.List;
import java.util.Queue;

@ThreadSafe
public class ModelSchemaExtractor {

    private final static List<ModelSchemaExtractionStrategy<?>> EXTRACTION_STRATEGIES = ImmutableList.<ModelSchemaExtractionStrategy<?>>of(
            new ManagedTypeModelSchemaExtractionStrategy(),
            new ManagedSetSchemaExtractionStrategy(),
            new UnmanagedTypeSchemaExtractionStrategy()
    );

    public <T> ModelSchema<T> extract(ModelType<T> type, ModelSchemaCache cache) {
        Queue<ModelSchemaExtractionContext<?>> unsatisfiedDependencies = Lists.newLinkedList();
        ModelSchemaExtractionContext<?> extractionContext = ModelSchemaExtractionContext.root(type);
        while (extractionContext != null) {
            ModelSchemaExtractionResult<?> nextSchema = extractSchema(extractionContext, cache);
            pushUnsatisfiedDependencies(nextSchema.getDependencies(), unsatisfiedDependencies, cache);
            extractionContext = unsatisfiedDependencies.poll();
        }

        return cache.get(type);
    }

    private void pushUnsatisfiedDependencies(Iterable<? extends ModelSchemaExtractionContext<?>> allDependencies, Queue<ModelSchemaExtractionContext<?>> dependencyQueue, final ModelSchemaCache cache) {
        Iterables.addAll(dependencyQueue, Iterables.filter(allDependencies, new Predicate<ModelSchemaExtractionContext<?>>() {
            public boolean apply(ModelSchemaExtractionContext<?> dependency) {
                return cache.get(dependency.getType()) == null;
            }
        }));
    }

    private <T> ModelSchemaExtractionResult<T> extractSchema(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaCache cache) {
        final ModelType<T> type = extractionContext.getType();
        ModelSchema<T> cached = cache.get(type);
        if (cached != null) {
            return new ModelSchemaExtractionResult<T>(cached);
        }

        ModelSchemaExtractionStrategy<?> strategy = Iterables.find(EXTRACTION_STRATEGIES, new Predicate<ModelSchemaExtractionStrategy<?>>() {
            public boolean apply(ModelSchemaExtractionStrategy<?> candidate) {
                if (candidate.getType().isAssignableFrom(type)) {
                    ModelSchemaExtractionStrategy<? super T> castCandidate = Cast.uncheckedCast(candidate);
                    return castCandidate.getSpec().isSatisfiedBy(type);
                } else {
                    return false;
                }
            }
        });

        ModelSchemaExtractionStrategy<? super T> castStrategy = Cast.uncheckedCast(strategy);
        ModelSchemaExtractionResult<T> schemaExtraction = castStrategy.extract(extractionContext, cache);
        cache.set(type, schemaExtraction.getSchema());
        return schemaExtraction;
    }

}
