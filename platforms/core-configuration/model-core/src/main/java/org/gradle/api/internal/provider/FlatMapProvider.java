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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.internal.evaluation.EvaluationContext;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

public class FlatMapProvider<S, T> extends AbstractMinimalProvider<S> implements TaskContextScope {
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
            return new ResultProducer(backingProvider(context, ValueConsumer.IgnoreUnsafeRead).getProducer());
        }
    }

    /**
     * The result of the transformation is reached through the task that the source value is reached through, for example when the source is a task provider.
     * This does not make the task of the source a producer of the result.
     */
    @Nullable
    @Override
    public Task getTaskContext() {
        return provider.getProducer().getTaskContext();
    }

    /**
     * The producer of the result of the transformation.
     * It carries the task context of this provider, so that the context is available to a transformation of the result.
     */
    private class ResultProducer implements ValueProducer {
        private final ValueProducer delegate;

        ResultProducer(ValueProducer delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isKnown() {
            return inScope(delegate::isKnown);
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
            inScope(() -> {
                delegate.visitProducerTasks(visitor);
                return true;
            });
        }

        @Override
        public void visitContentProducerTasks(Action<? super Task> visitor) {
            inScope(() -> {
                delegate.visitContentProducerTasks(visitor);
                return true;
            });
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            inScope(() -> {
                delegate.visitDependencies(context);
                return true;
            });
        }

        @Nullable
        @Override
        public Task getTaskContext() {
            Task context = delegate.getTaskContext();
            return context != null ? context : FlatMapProvider.this.getTaskContext();
        }

        /**
         * Some producers are calculated when they are queried, so make the task context of this provider available to them.
         */
        private boolean inScope(BooleanSupplier query) {
            boolean[] result = new boolean[1];
            boolean evaluated = EvaluationContext.current().tryEvaluate(FlatMapProvider.this, false, () -> {
                result[0] = query.getAsBoolean();
                return true;
            });
            // Not evaluated when this provider is already being evaluated, in which case its task context is already available
            return evaluated ? result[0] : query.getAsBoolean();
        }
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
