/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public class FlatMapProvider<S, T> extends AbstractMinimalProvider<S> {
    private final ProviderInternal<? extends T> provider;
    private final Transformer<? extends Provider<? extends S>, ? super T> transformer;

    FlatMapProvider(ProviderInternal<? extends T> provider, Transformer<? extends Provider<? extends S>, ? super T> transformer) {
        this.provider = provider;
        this.transformer = transformer;
    }

    @Nullable
    @Override
    public Class<S> getType() {
        return null;
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            return backingProvider(context, consumer).calculatePresence(consumer);
        }
    }

    @Override
    protected Value<? extends S> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            Value<? extends T> value = provider.calculateValue(consumer);
            if (value.isMissing()) {
                return value.asType();
            }
            return doMapValue(context, value).calculateValue(consumer);
        }
    }

    private ProviderInternal<? extends S> doMapValue(EvaluationScopeContext ignored, Value<? extends T> value) {
        T unpackedValue = value.getWithoutSideEffect();
        Provider<? extends S> transformedProvider = transformer.transform(unpackedValue);
        if (transformedProvider == null) {
            return Providers.notDefined();
        }

        // Note, that the potential side effect of the transformed provider
        // is going to be executed before this fixed side effect.
        // It is not possible to preserve linear execution order in the general case,
        // as the transformed provider can have side effects hidden under other wrapping providers.
        return Providers.internal(transformedProvider).withSideEffect(SideEffect.fixedFrom(value));
    }

    private ProviderInternal<? extends S> backingProvider(EvaluationScopeContext context, ValueConsumer consumer) {
        Value<? extends T> value = provider.calculateValue(consumer);
        if (value.isMissing()) {
            return Providers.notDefined();
        }
        return doMapValue(context, value);
    }

    @Override
    public ValueProducer getProducer() {
        try (EvaluationScopeContext context = openScope()) {
            ValueProducer producer = backingProvider(context, ValueConsumer.IgnoreUnsafeRead).getProducer();
            if (producer.hasUnownedOutput()) {
                // The transformation returned an output that does not know which task produces it.
                // When the source is a task provider, the output was reached through that task, so the task is its producer.
                Task owner = getTaskWhoseStateIsTheSource();
                if (owner != null) {
                    return producer.withOutputOwner(owner);
                }
            }
            return producer;
        }
    }

    /**
     * Returns the task when the source value is calculated from the state of exactly one task, for example when the source is a task provider.
     * A source whose content is produced by a task, such as a task output, is not such a source.
     */
    @Nullable
    private Task getTaskWhoseStateIsTheSource() {
        ValueProducer sourceProducer = provider.getProducer();
        if (sourceProducer.hasUnownedOutput()) {
            return null;
        }
        Set<Task> tasks = new LinkedHashSet<>(2);
        sourceProducer.visitProducerTasks(tasks::add);
        if (tasks.size() != 1) {
            return null;
        }
        Set<Task> contentProducers = new LinkedHashSet<>(1);
        sourceProducer.visitContentProducerTasks(contentProducers::add);
        return contentProducers.isEmpty() ? tasks.iterator().next() : null;
    }

    @Override
    public ExecutionTimeValue<? extends S> calculateExecutionTimeValue() {
        try (EvaluationScopeContext context = openScope()) {
            return backingProvider(context, ValueConsumer.IgnoreUnsafeRead).calculateExecutionTimeValue();
        }
    }

    @Override
    protected String toStringNoReentrance() {
        return "flatmap(" + provider + ")";
    }
}
