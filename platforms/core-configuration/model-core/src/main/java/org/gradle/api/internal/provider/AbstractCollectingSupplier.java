/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

abstract class AbstractCollectingSupplier<COLLECTOR extends ValueSupplier, TYPE> extends AbstractMinimalProvider<TYPE> {
    // The underlying list is shared by the collectors produced by `plus`, so we don't have to copy the collectors every time.
    // However, this also means that you can only call plus on a given collector once.
    protected final AppendOnceList<COLLECTOR> collectors;

    public AbstractCollectingSupplier(AppendOnceList<COLLECTOR> collectors) {
        this.collectors = collectors;
    }

    @Override
    public final Value<? extends TYPE> calculateValue(ValueConsumer consumer) {
        return super.calculateValue(consumer);
    }

    @Override
    public ValueProducer getProducer() {
        return new ValueProducer() {
            @Override
            public void visitProducerTasks(Action<? super Task> visitor) {
                getProducers().forEach(c -> c.visitProducerTasks(visitor));
            }

            @Override
            public boolean isKnown() {
                return getProducers().anyMatch(ValueProducer::isKnown);
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                getProducers().forEach(c -> c.visitDependencies(context));
            }

            @Override
            public void visitContentProducerTasks(Action<? super Task> visitor) {
                getProducers().forEach(c -> c.visitContentProducerTasks(visitor));
            }
        };
    }

    @Override
    public final boolean calculatePresence(ValueConsumer consumer) {
        for (COLLECTOR collector : collectors) {
            if (!collector.calculatePresence(consumer)) {
                // When any contributor is missing, the whole property is missing.
                return false;
            }
        }
        return true;
    }

    protected <ENTRIES, BUILDER> Value<ENTRIES> calculateValue(
        BiFunction<BUILDER, COLLECTOR, Value<Void>> collectEntriesForCollector,
        BUILDER builder,
        Function<BUILDER, ENTRIES> buildEntries
    ) {
        SideEffectBuilder<ENTRIES> sideEffects = SideEffect.builder();
        for (COLLECTOR collector : collectors) {
            Value<Void> result = collectEntriesForCollector.apply(builder, collector);
            if (result.isMissing()) {
                // When any contributor is missing, the whole property is missing.
                return result.asType();
            }
            sideEffects.add(SideEffect.fixedFrom(result));
        }
        return Value.of(buildEntries.apply(builder)).withSideEffect(sideEffects.build());
    }

    protected ExecutionTimeValue<? extends TYPE> calculateExecutionTimeValue(
        Function<COLLECTOR, ExecutionTimeValue<? extends TYPE>> calculateExecutionTimeValueForCollector,
        BiFunction<List<ExecutionTimeValue<? extends TYPE>>, SideEffectBuilder<TYPE>, ExecutionTimeValue<? extends TYPE>> calculateFixedExecutionTimeValue,
        Function<List<ExecutionTimeValue<? extends TYPE>>, ExecutionTimeValue<? extends TYPE>> calculateChangingExecutionTimeValue
    ) {
        List<ExecutionTimeValue<? extends TYPE>> executionTimeValues =
            collectExecutionTimeValues(calculateExecutionTimeValueForCollector);
        if (executionTimeValues.isEmpty()) {
            return ExecutionTimeValue.missing();
        }
        ExecutionTimeValue<? extends TYPE> fixedOrMissing = fixedOrMissingValueOf(executionTimeValues, calculateFixedExecutionTimeValue);
        if (fixedOrMissing != null) {
            return fixedOrMissing;
        }
        return calculateChangingExecutionTimeValue.apply(executionTimeValues);
    }

    /**
     * Returns an empty list when the overall value is missing.
     */
    protected List<ExecutionTimeValue<? extends TYPE>> collectExecutionTimeValues(Function<COLLECTOR, ExecutionTimeValue<? extends TYPE>> calculateExecutionTimeValueForCollector) {
        ImmutableList.Builder<ExecutionTimeValue<? extends TYPE>> executionTimeValues = ImmutableList.builderWithExpectedSize(collectors.size());

        for (COLLECTOR collector : collectors) {
            ExecutionTimeValue<? extends TYPE> result = calculateExecutionTimeValueForCollector.apply(collector);
            if (result.isMissing()) {
                // If any of the property elements is missing, the property is missing too.
                return ImmutableList.of();
            }
            executionTimeValues.add(result);
        }
        // No missing values found, so all the candidates are part of the final value.
        return executionTimeValues.build();
    }

    @Override
    protected String toStringNoReentrance() {
        StringBuilder sb = new StringBuilder();
        collectors.forEach(collector -> {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(collector.toString());
        });
        return sb.toString();
    }

    /**
     * Try to simplify the set of execution values to either a missing value or a fixed value.
     */
    @Nullable
    private ExecutionTimeValue<? extends TYPE> fixedOrMissingValueOf(
        List<ExecutionTimeValue<? extends TYPE>> values,
        BiFunction<List<ExecutionTimeValue<? extends TYPE>>, SideEffectBuilder<TYPE>, ExecutionTimeValue<? extends TYPE>> calculateFixedExecutionTimeValue
    ) {
        boolean fixed = true;
        boolean changingContent = false;
        for (ExecutionTimeValue<? extends TYPE> value : values) {
            if (value.isMissing()) {
                return ExecutionTimeValue.missing();
            }
            if (value.isChangingValue()) {
                fixed = false;
            } else if (value.hasChangingContent()) {
                changingContent = true;
            }
        }
        if (fixed) {
            SideEffectBuilder<TYPE> sideEffectBuilder = SideEffect.builder();
            ExecutionTimeValue<? extends TYPE> fixedValue = calculateFixedExecutionTimeValue.apply(values, sideEffectBuilder);
            fixedValue = changingContent ? fixedValue.withChangingContent() : fixedValue;
            return fixedValue.withSideEffect(sideEffectBuilder.build());
        }
        return null;
    }

    private Stream<ValueProducer> getProducers() {
        return collectors.stream().map(ValueSupplier::getProducer);
    }
}
