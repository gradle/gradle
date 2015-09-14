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
import org.gradle.internal.SystemProperties;
import org.gradle.model.Managed;
import org.gradle.model.ModelMap;
import org.gradle.model.ModelSet;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

@ThreadSafe
public class ModelSchemaExtractor {

    private final List<? extends ModelSchemaExtractionStrategy> strategies;

    public ModelSchemaExtractor() {
        this(Collections.<ModelSchemaExtractionStrategy>emptyList(), new ModelSchemaAspectExtractor());
    }

    public ModelSchemaExtractor(List<? extends ModelSchemaExtractionStrategy> strategies, ModelSchemaAspectExtractor aspectExtractor) {
        this.strategies = ImmutableList.<ModelSchemaExtractionStrategy>builder()
            .addAll(strategies)
            .add(new PrimitiveStrategy())
            .add(new EnumStrategy())
            .add(new JdkValueTypeStrategy())
            .add(new ModelSetStrategy())
            .add(new ManagedSetStrategy())
            .add(new SpecializedMapStrategy())
            .add(new ModelMapStrategy())
            .add(new ScalarCollectionStrategy())
            .add(new ManagedImplStructStrategy(aspectExtractor))
            .add(new UnmanagedImplStructStrategy(aspectExtractor))
            .build();
    }

    public <T> ModelSchema<T> extract(ModelType<T> type, ModelSchemaStore store, ModelSchemaCache cache) {
        ModelSchemaExtractionContext<T> context = ModelSchemaExtractionContext.root(type);
        List<ModelSchemaExtractionContext<?>> validations = Lists.newLinkedList();
        Queue<ModelSchemaExtractionContext<?>> unsatisfiedDependencies = Lists.newLinkedList();
        ModelSchemaExtractionContext<?> extractionContext = context;
        validations.add(extractionContext);

        while (extractionContext != null) {
            extractSchema(extractionContext, store, cache);
            Iterable<? extends ModelSchemaExtractionContext<?>> dependencies = extractionContext.getChildren();
            Iterables.addAll(validations, dependencies);
            pushUnsatisfiedDependencies(dependencies, unsatisfiedDependencies, cache);
            extractionContext = unsatisfiedDependencies.poll();
        }

        for (ModelSchemaExtractionContext<?> validationContext : Lists.reverse(validations)) {
            // TODO - this will leave invalid types in the cache when it fails
            validate(validationContext, cache);
        }

        return context.getResult();
    }

    private void pushUnsatisfiedDependencies(Iterable<? extends ModelSchemaExtractionContext<?>> allDependencies, Queue<ModelSchemaExtractionContext<?>> dependencyQueue, final ModelSchemaCache cache) {
        Iterables.addAll(dependencyQueue, Iterables.filter(allDependencies, new Predicate<ModelSchemaExtractionContext<?>>() {
            public boolean apply(ModelSchemaExtractionContext<?> dependency) {
                return cache.get(dependency.getType()) == null;
            }
        }));
    }

    private <T> void validate(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaCache cache) {
        extractionContext.validate(cache.get(extractionContext.getType()));
    }

    private <T> void extractSchema(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaStore store, ModelSchemaCache cache) {
        final ModelType<T> type = extractionContext.getType();
        ModelSchema<T> cached = cache.get(type);
        if (cached != null) {
            extractionContext.found(cached);
            return;
        }

        for (ModelSchemaExtractionStrategy strategy : strategies) {
            strategy.extract(extractionContext, store);
            if (extractionContext.getResult() != null) {
                cache.set(type, extractionContext.getResult());
                return;
            }
        }

        // Should never get here, the last strategy should be a catch all
        throw new IllegalStateException("No extraction strategy found for type: " + type);
    }

    public static String getManageablePropertyTypesDescription() {
        return Joiner.on(SystemProperties.getInstance().getLineSeparator()).join(Iterables.transform(getSupportedTypes(), new Function<String, String>() {
            public String apply(String input) {
                return " - " + input;
            }
        }));
    }

    private static Iterable<String> getSupportedTypes() {
        return Arrays.asList(
            "interfaces and abstract classes annotated with " + Managed.class.getName(),
            "JDK value types: " + Joiner.on(", ").join(Iterables.transform(ScalarTypes.TYPES, new Function<ModelType<?>, Object>() {
                public Object apply(ModelType<?> input) {
                    return input.getRawClass().getSimpleName();
                }
            })),
            "Enum types",
            ModelMap.class.getName() + " of a managed type",
            ModelSet.class.getName() + " of a managed type"
        );
    }

}
