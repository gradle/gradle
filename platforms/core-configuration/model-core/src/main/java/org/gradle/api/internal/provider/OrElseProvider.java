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

import org.gradle.internal.evaluation.EvaluationScopeContext;

import javax.annotation.Nullable;

class OrElseProvider<T> extends AbstractMinimalProvider<T> {
    private final ProviderInternal<T> left;
    private final ProviderInternal<? extends T> right;

    public OrElseProvider(ProviderInternal<T> left, ProviderInternal<? extends T> right) {
        this.left = left;
        this.right = right;
    }

    @Override
    protected String toStringNoReentrance() {
        return String.format("or(%s, %s)", left, right);
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return left.getType();
    }

    @Override
    public ValueProducer getProducer() {
        try (EvaluationScopeContext context = openScope()) {
            return new OrElseValueProducer(context, left, right);
        }
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            return left.calculatePresence(consumer) || right.calculatePresence(consumer);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        try (EvaluationScopeContext ignored = openScope()) {
            ExecutionTimeValue<? extends T> leftValue = left.calculateExecutionTimeValue();
            if (leftValue.hasFixedValue()) {
                return leftValue;
            }
            ExecutionTimeValue<? extends T> rightValue = right.calculateExecutionTimeValue();
            if (leftValue.isMissing()) {
                return rightValue;
            }
            if (rightValue.isMissing()) {
                // simplify
                return leftValue;
            }
            return ExecutionTimeValue.changingValue(
                new OrElseProvider(
                    leftValue.getChangingValue(),
                    rightValue.toProvider()
                )
            );
        }
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            Value<? extends T> leftValue = left.calculateValue(consumer);
            if (!leftValue.isMissing()) {
                return leftValue;
            }
            Value<? extends T> rightValue = right.calculateValue(consumer);
            if (!rightValue.isMissing()) {
                return rightValue;
            }
            return leftValue.addPathsFrom(rightValue);
        }
    }
}
