/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.internal.state.ModelObject;
import org.jspecify.annotations.Nullable;

/**
 * Decorates a provider that has been declared as an output property of a task, so that the provider carries
 * its producing task wherever it is consumed, e.g. via {@code taskProvider.flatMap { it.output }}.
 *
 * <p>Lazy properties record their producer themselves (see {@link PropertyInternal}). Any other provider is
 * wrapped in this decorator when the output role is applied to the value returned from an annotated, overridable
 * getter (the generated getter returns the decorator), or when the value is registered through {@code TaskOutputs}.
 * Keeping the producer in a decorator rather than in a field of {@link AbstractMinimalProvider} means only the few
 * providers used as task outputs pay for it.</p>
 */
public class ProducerBackedProvider<T> extends AbstractMinimalProvider<T> implements ProducerAware {
    private final ProviderInternal<T> delegate;
    private final ModelObject producer;

    /**
     * Declares the given provider as an output of the given model object. Providers that can record the
     * producer themselves are returned as is, any other provider is wrapped.
     */
    public static <T> ProviderInternal<T> of(ProviderInternal<T> provider, ModelObject producer) {
        if (provider instanceof ProducerAware) {
            ((ProducerAware) provider).attachProducer(producer);
            return provider;
        }
        return new ProducerBackedProvider<>(provider, producer);
    }

    private ProducerBackedProvider(ProviderInternal<T> delegate, ModelObject producer) {
        this.delegate = delegate;
        this.producer = producer;
    }

    public ProviderInternal<T> getDelegate() {
        return delegate;
    }

    /**
     * The task that produces the value, or {@code null} when the producer is not (yet) owned by a task.
     */
    @Nullable
    public Task getProducerTask() {
        return producer.getTaskThatOwnsThisObject();
    }

    @Override
    public void attachProducer(ModelObject owner) {
        if (owner == producer) {
            return;
        }
        // The output role may be applied to this provider again by a nested bean of the producing task, or after
        // deserialization from the configuration cache. Both are the same producer as long as the task is the same.
        Task ownerTask = owner.getTaskThatOwnsThisObject();
        if (ownerTask != null && ownerTask == producer.getTaskThatOwnsThisObject()) {
            return;
        }
        OutputProperties.assertCanAttachProducer(producer, owner, getDisplayName());
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return delegate.getType();
    }

    @Override
    public ValueProducer getProducer() {
        Task task = OutputProperties.producerTaskOf(producer, getDisplayName());
        return task == null ? ValueProducer.noProducer() : ValueProducer.task(task);
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return delegate.calculatePresence(consumer);
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return delegate.calculateValue(consumer);
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        // The contents of the value are produced by the task, as for a lazy property declared as an output
        return delegate.calculateExecutionTimeValue().withChangingContent();
    }

    @Override
    protected String toStringNoReentrance() {
        return delegate.toString();
    }
}
