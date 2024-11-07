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

import org.gradle.api.provider.Provider;
import org.gradle.internal.evaluation.EvaluationScopeContext;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

public class BiProvider<R, A, B> extends AbstractMinimalProvider<R> {

    private final Class<R> type;
    private final BiFunction<? super A, ? super B, ? extends R> combiner;
    private final ProviderInternal<A> left;
    private final ProviderInternal<B> right;

    public BiProvider(@Nullable Class<R> type, Provider<A> left, Provider<B> right, BiFunction<? super A, ? super B, ? extends R> combiner) {
        this.type = type;
        this.combiner = combiner;
        this.left = Providers.internal(left);
        this.right = Providers.internal(right);
    }

    @Override
    protected String toStringNoReentrance() {
        return String.format("and(%s, %s)", left, right);
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            if (!left.calculatePresence(consumer) || !right.calculatePresence(consumer)) {
                return false;
            }
        }
        // Purposefully only calculate full value if left & right are both present, to save time
        return super.calculatePresence(consumer);
    }

    @Override
    public ExecutionTimeValue<? extends R> calculateExecutionTimeValue() {
        try (EvaluationScopeContext ignored = openScope()) {
            if (isChangingValue(left) || isChangingValue(right)) {
                return ExecutionTimeValue.changingValue(this);
            }
        }
        return super.calculateExecutionTimeValue();
    }

    private static boolean isChangingValue(ProviderInternal<?> provider) {
        return provider.calculateExecutionTimeValue().isChangingValue();
    }

    @Override
    protected Value<? extends R> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            Value<? extends A> leftValue = left.calculateValue(consumer);
            if (leftValue.isMissing()) {
                return leftValue.asType();
            }
            Value<? extends B> rightValue = right.calculateValue(consumer);
            if (rightValue.isMissing()) {
                return rightValue.asType();
            }

            R combinedUnpackedValue = combiner.apply(leftValue.getWithoutSideEffect(), rightValue.getWithoutSideEffect());

            return Value.ofNullable(combinedUnpackedValue)
                .withSideEffect(SideEffect.fixedFrom(leftValue))
                .withSideEffect(SideEffect.fixedFrom(rightValue));
        }
    }

    @Nullable
    @Override
    public Class<R> getType() {
        return type;
    }

    @Override
    public ValueProducer getProducer() {
        try (EvaluationScopeContext ignored = openScope()) {
            return new PlusProducer(left.getProducer(), right.getProducer());
        }
    }
}
