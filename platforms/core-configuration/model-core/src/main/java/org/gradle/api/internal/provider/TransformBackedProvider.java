/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Transformer;
import org.gradle.internal.evaluation.EvaluationScopeContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <p>A mapping provider that uses a transform for which {@link MappingProvider} cannot be used.
 * This implementation is used for user provided transforms and also for internal transforms that don't meet the constraints of {@link MappingProvider}.</p>
 *
 * <p>This provider checks that the contents of value have been built prior to running the transform, as the transform may use the content.
 * This check should move further upstream in the future, closer to the producer of the content.</p>
 *
 * @see ProviderInternal for a discussion of the "value" and "value contents".
 */
public class TransformBackedProvider<OUT, IN> extends AbstractMinimalProvider<OUT> {

    protected final Class<OUT> type;
    protected final ProviderInternal<? extends IN> provider;
    protected final Transformer<? extends OUT, ? super IN> transformer;

    public TransformBackedProvider(
        @Nullable Class<OUT> type,
        ProviderInternal<? extends IN> provider,
        Transformer<? extends OUT, ? super IN> transformer
    ) {
        this.type = type;
        this.transformer = transformer;
        this.provider = provider;
    }

    @Nullable
    @Override
    public Class<OUT> getType() {
        return type;
    }

    @Override
    public ValueProducer getProducer() {
        try (EvaluationScopeContext ignored = openScope()) {
            return provider.getProducer();
        }
    }

    @Override
    public ExecutionTimeValue<? extends OUT> calculateExecutionTimeValue() {
        try (EvaluationScopeContext context = openScope()) {
            ExecutionTimeValue<? extends IN> value = provider.calculateExecutionTimeValue();
            if (value.hasChangingContent()) {
                // Need the value contents in order to transform it to produce the value of this provider,
                // so if the value or its contents are built by tasks, the value of this provider is also built by tasks
                return ExecutionTimeValue.changingValue(new TransformBackedProvider<OUT, IN>(type, value.toProvider(), transformer));
            }

            return ExecutionTimeValue.value(mapValue(context, value.toValue()));
        }
    }

    @Override
    protected Value<? extends OUT> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            beforeRead(context);
            Value<? extends IN> value = provider.calculateValue(consumer);
            return mapValue(context, value);
        }
    }

    @Nonnull
    protected Value<OUT> mapValue(EvaluationScopeContext context, Value<? extends IN> value) {
        if (value.isMissing()) {
            return value.asType();
        }
        return value.transform(transformer);
    }

    protected void beforeRead(EvaluationScopeContext context) {
        provider.getProducer().visitContentProducerTasks(producer -> {
            if (!producer.getState().getExecuted()) {
                throw new InvalidUserCodeException(
                    String.format("Querying the mapped value of %s before %s has completed is not supported", provider, producer)
                );
            }
        });
    }

    @Override
    protected String toStringNoReentrance() {
        return "map(" + (type == null ? "" : type.getName() + " ") + provider + ")";
    }
}
