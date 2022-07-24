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

import org.gradle.api.Transformer;

import javax.annotation.Nullable;

/**
 * A mapping provider that uses a transform that 1. does not use the value contents and 2. always produces a value.
 */
public class MappingProvider<OUT, IN> extends AbstractMinimalProvider<OUT> {
    private final Class<OUT> type;
    private final ProviderInternal<? extends IN> provider;
    private final Transformer<? extends OUT, ? super IN> transformer;

    public MappingProvider(Class<OUT> type, ProviderInternal<? extends IN> provider, Transformer<? extends OUT, ? super IN> transformer) {
        this.type = type;
        this.provider = provider;
        this.transformer = transformer;
    }

    @Nullable
    @Override
    public Class<OUT> getType() {
        return type;
    }

    @Override
    public ValueProducer getProducer() {
        return provider.getProducer();
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return provider.calculatePresence(consumer);
    }

    @Override
    protected Value<OUT> calculateOwnValue(ValueConsumer consumer) {
        Value<? extends IN> value = provider.calculateValue(consumer);
        if (value.isMissing()) {
            return value.asType();
        }
        return Value.of(transformer.transform(value.get()));
    }

    @Override
    public ExecutionTimeValue<? extends OUT> calculateExecutionTimeValue() {
        ExecutionTimeValue<? extends IN> value = provider.calculateExecutionTimeValue();
        if (value.isChangingValue()) {
            return ExecutionTimeValue.changingValue(new MappingProvider<OUT, IN>(type, value.getChangingValue(), transformer));
        } else if (value.isMissing()) {
            return ExecutionTimeValue.missing();
        } else {
            return ExecutionTimeValue.fixedValue(transformer.transform(value.getFixedValue()));
        }
    }

    @Override
    public String toString() {
        return "map(" + type.getName() + " " + provider + " " + transformer + ")";
    }
}
