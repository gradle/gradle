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
import org.gradle.internal.evaluation.EvaluationScopeContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <p>A mapping provider that uses a transform that:</p>
 *
 * <ul>
 *     <li>1. does not use the value contents</li>
 *     <li>2. always produces a non-null value.</li>
 * </ul>
 *
 * <p>This implementation is used only for internal transforms where these constraints are known to be true.
 * For user provided mappings and other internal mappings, {@link TransformBackedProvider} is used instead.</p>
 *
 * <p>The constraints allows certain optimizations. Currently, this is limited to skipping the transform when the provider presence is queried, but other optimizations may be added in the future.
 * Also, because the transform does not use the value content, this provider also skips checks to verify that the content has been built when the value is queried.</p>
 *
 * @see ProviderInternal for a discussion of the "value" and "value contents".
 */
public class MappingProvider<OUT, IN> extends TransformBackedProvider<OUT, IN> {

    public MappingProvider(@Nullable Class<OUT> type, ProviderInternal<? extends IN> provider, Transformer<? extends OUT, ? super IN> transformer) {
        super(type, provider, transformer);
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        // Rely on MappingProvider contract with regard to the transform always returning value
        try (EvaluationScopeContext ignored = openScope()) {
            return provider.calculatePresence(consumer);
        }
    }

    @Override
    public ExecutionTimeValue<? extends OUT> calculateExecutionTimeValue() {
        try (EvaluationScopeContext context = openScope()) {
            ExecutionTimeValue<? extends IN> value = provider.calculateExecutionTimeValue();
            if (value.isChangingValue()) {
                return ExecutionTimeValue.changingValue(new MappingProvider<OUT, IN>(type, value.getChangingValue(), transformer));
            }

            return ExecutionTimeValue.value(mapValue(context, value.toValue()));
        }
    }

    @Nonnull
    @Override
    protected Value<OUT> mapValue(EvaluationScopeContext context, Value<? extends IN> value) {
        Value<OUT> transformedValue = super.mapValue(context, value);
        // Check MappingProvider contract with regard to the transform
        if (!value.isMissing() && transformedValue.isMissing()) {
            throw new IllegalStateException("The transformer in MappingProvider must always return a value");
        }
        return transformedValue;
    }

    @Override
    protected void beforeRead(EvaluationScopeContext context) {}

    @Override
    protected String toStringNoReentrance() {
        return "map(" + (type == null ? "" : type.getName() + " ") + provider + " " + transformer + ")";
    }
}
