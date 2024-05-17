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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

public class DefaultModelSchemaExtractor implements ModelSchemaExtractor {

    private final List<? extends ModelSchemaExtractionStrategy> strategies;

    public static DefaultModelSchemaExtractor withDefaultStrategies(List<? extends ModelSchemaExtractionStrategy> strategies, ModelSchemaAspectExtractor aspectExtractor) {
        return new DefaultModelSchemaExtractor(ImmutableList.<ModelSchemaExtractionStrategy>builder()
            .addAll(strategies)
            .add(new PrimitiveStrategy())
            .add(new EnumStrategy())
            .add(new JdkValueTypeStrategy())
            .add(new ModelSetStrategy())
            .add(new SpecializedMapStrategy())
            .add(new ModelMapStrategy())
            .add(new JavaUtilCollectionStrategy())
            .add(new ManagedImplStructStrategy(aspectExtractor))
            .add(new RuleSourceSchemaExtractionStrategy(aspectExtractor))
            .add(new UnmanagedImplStructStrategy(aspectExtractor))
            .build());
    }

    public static DefaultModelSchemaExtractor withDefaultStrategies() {
        return withDefaultStrategies(Collections.<ModelSchemaExtractionStrategy>emptyList(), new ModelSchemaAspectExtractor());
    }

    public DefaultModelSchemaExtractor(List<? extends ModelSchemaExtractionStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public <T> ModelSchema<T> extract(ModelType<T> type, ModelSchemaCache cache) {
        DefaultModelSchemaExtractionContext<T> context = DefaultModelSchemaExtractionContext.root(type);
        List<DefaultModelSchemaExtractionContext<?>> validations = Lists.newArrayList();
        Queue<DefaultModelSchemaExtractionContext<?>> unsatisfiedDependencies = new ArrayDeque<DefaultModelSchemaExtractionContext<?>>();
        DefaultModelSchemaExtractionContext<?> extractionContext = context;
        validations.add(extractionContext);

        while (extractionContext != null) {
            extractSchema(extractionContext, cache);
            Iterable<DefaultModelSchemaExtractionContext<?>> dependencies = extractionContext.getChildren();
            Iterables.addAll(validations, dependencies);
            pushUnsatisfiedDependencies(dependencies, unsatisfiedDependencies, cache);
            extractionContext = unsatisfiedDependencies.poll();
        }

        for (DefaultModelSchemaExtractionContext<?> validationContext : Lists.reverse(validations)) {
            // TODO - this will leave invalid types in the cache when it fails
            validate(validationContext, cache);
        }

        return context.getResult();
    }

    private void pushUnsatisfiedDependencies(Iterable<? extends DefaultModelSchemaExtractionContext<?>> allDependencies, Queue<DefaultModelSchemaExtractionContext<?>> dependencyQueue, final ModelSchemaCache cache) {
        Iterables.addAll(dependencyQueue, Iterables.filter(allDependencies, new Predicate<ModelSchemaExtractionContext<?>>() {
            @Override
            public boolean apply(ModelSchemaExtractionContext<?> dependency) {
                return cache.get(dependency.getType()) == null;
            }
        }));
    }

    private <T> void validate(DefaultModelSchemaExtractionContext<T> extractionContext, ModelSchemaCache cache) {
        extractionContext.validate(cache.get(extractionContext.getType()));
    }

    private <T> void extractSchema(DefaultModelSchemaExtractionContext<T> extractionContext, ModelSchemaCache cache) {
        final ModelType<T> type = extractionContext.getType();
        ModelSchema<T> cached = cache.get(type);
        if (cached != null) {
            extractionContext.found(cached);
            return;
        }

        for (ModelSchemaExtractionStrategy strategy : strategies) {
            strategy.extract(extractionContext);
            if (extractionContext.hasProblems()) {
                throw new InvalidManagedModelElementTypeException(extractionContext);
            }
            if (extractionContext.getResult() != null) {
                cache.set(type, extractionContext.getResult());
                return;
            }
        }

        // Should never get here, the last strategy should be a catch all
        throw new IllegalStateException("No extraction strategy found for type: " + type);
    }
}
