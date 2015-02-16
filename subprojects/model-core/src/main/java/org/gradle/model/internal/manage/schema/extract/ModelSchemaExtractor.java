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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.util.List;
import java.util.Queue;

@ThreadSafe
class ModelSchemaExtractor {

    private final Factory<String> supportedTypeDescriptions = new Factory<String>() {
        public String create() {
            return getSupportedTypesDescription();
        }
    };
    private final List<ModelSchemaExtractionStrategy> strategies = ImmutableList.of(
            new PrimitiveStrategy(),
            new EnumStrategy(),
            new JdkValueTypeStrategy(),
            new ManagedSetStrategy(supportedTypeDescriptions),
            new StructStrategy(supportedTypeDescriptions),
            new UnmanagedStrategy()
    );

    public <T> ModelSchema<T> extract(ModelType<T> type, ModelSchemaCache cache) {
        ModelSchemaExtractionContext<T> context = ModelSchemaExtractionContext.root(type);
        List<ModelSchemaExtractionContext<?>> validations = Lists.newLinkedList();
        Queue<ModelSchemaExtractionContext<?>> unsatisfiedDependencies = Lists.newLinkedList();
        ModelSchemaExtractionContext<?> extractionContext = context;
        validations.add(extractionContext);

        while (extractionContext != null) {
            ModelSchemaExtractionResult<?> nextSchema = extractSchema(extractionContext, cache);
            Iterable<? extends ModelSchemaExtractionContext<?>> dependencies = nextSchema.getDependencies();
            Iterables.addAll(validations, dependencies);
            pushUnsatisfiedDependencies(dependencies, unsatisfiedDependencies, cache);
            extractionContext = unsatisfiedDependencies.poll();
        }

        for (ModelSchemaExtractionContext<?> validationContext : Lists.reverse(validations)) {
            validationContext.validate();
        }

        return cache.get(context.getType());
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

        for (ModelSchemaExtractionStrategy strategy : strategies) {
            ModelSchemaExtractionResult<T> result = strategy.extract(extractionContext, cache);
            if (result != null) {
                cache.set(type, result.getSchema());
                return result;
            }
        }

        // Should never get here, the last strategy should be a catch all
        throw new IllegalStateException("No extraction strategy found for type: " + type);
    }

    private String getSupportedTypesDescription() {
        return Joiner.on(SystemProperties.getInstance().getLineSeparator()).join(Iterables.transform(getSupportedTypes(), new Function<String, String>() {
            public String apply(String input) {
                return " - " + input;
            }
        }));
    }

    private Iterable<String> getSupportedTypes() {
        return Iterables.concat(Iterables.transform(strategies, new Function<ModelSchemaExtractionStrategy, Iterable<String>>() {
            public Iterable<String> apply(ModelSchemaExtractionStrategy input) {
                return input.getSupportedManagedTypes();
            }
        }));
    }

}
