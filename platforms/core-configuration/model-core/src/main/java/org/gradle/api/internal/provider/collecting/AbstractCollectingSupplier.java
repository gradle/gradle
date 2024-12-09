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

package org.gradle.api.internal.provider.collecting;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NonNullApi
public abstract class AbstractCollectingSupplier<CollectorType, Type> extends AbstractMinimalProvider<Type> {
    private final Predicate<CollectorType> checkAbsentIgnoring;
    // This list is shared by the collectors produced by `plus`, so we don't have to copy the collectors every time.
    // However, this also means that you can only call plus on a given collector once.
    protected final List<CollectorType> collectors; // TODO - Replace with PersistentList? This may make value calculation inefficient because the PersistentList can only prepend to head.
    protected final int size;

    public AbstractCollectingSupplier(
        Predicate<CollectorType> checkAbsentIgnoring,
        List<CollectorType> collectors,
        int size
    ) {
        this.collectors = collectors;
        this.size = size;
        this.checkAbsentIgnoring = checkAbsentIgnoring;
    }

    protected List<CollectorType> getCollectors() {
        return collectors.subList(0, size);
    }

    protected boolean calculatePresence(Predicate<CollectorType> calculatePresenceForCollector) {
        // We're traversing the elements in reverse addition order.
        // When determining the presence of the value, the last argument wins.
        // See also #collectExecutionTimeValues().
        for (CollectorType collector : Lists.reverse(getCollectors())) {
            if (!calculatePresenceForCollector.test(collector)) {
                // We've found an argument of add/addAll that is missing.
                // It makes the property missing regardless of what has been added before.
                // Because of the reverse processing order, anything that was added after it was just add/addAll that do not change the presence.
                return false;
            }
            if (checkAbsentIgnoring.test(collector)) {
                // We've found an argument of append/appendAll, and everything added before it was present.
                // append/appendAll recovers the value of a missing property, so the property is also definitely present.
                return true;
            }
        }
        // We've found an argument of append/appendAll, and everything added before it was present.
        // append/appendAll recovers the value of a missing property, so the property is also definitely present.
        assert size > 0;
        return true;
    }

    protected <Entries, EntriesBuilder> Value<Entries> calculateValue(
        BiFunction<EntriesBuilder, CollectorType, Value<Void>> collectEntriesForCollector,
        Supplier<EntriesBuilder> builderSupplier,
        Function<EntriesBuilder, Entries> buildEntries
    ) {
        // TODO - don't make a copy when the collector already produces an immutable collection
        EntriesBuilder builder = builderSupplier.get();
        Value<Void> compositeResult = Value.present();
        for (CollectorType collector : getCollectors()) {
            if (compositeResult.isMissing() && !checkAbsentIgnoring.test(collector)) {
                // The property is missing so far and the argument is of add/addAll.
                // The property is going to be missing regardless of its value.
                continue;
            }
            Value<Void> result = collectEntriesForCollector.apply(builder, collector);
            if (result.isMissing()) {
                // This is the argument of add/addAll and it is missing. It "poisons" the property (it becomes missing).
                // We discard all values and side effects gathered so far.
                builder = builderSupplier.get();
                compositeResult = result;
            } else if (compositeResult.isMissing()) {
                assert checkAbsentIgnoring.test(collector);
                // This is an argument of append/appendAll. It "recovers" the property from the "poisoned" state.
                // Entries are already in the builder.
                compositeResult = result;
            } else {
                assert !compositeResult.isMissing();
                // Both the property so far and the current argument are present, just continue building the value.
                // Entries are already in the builder.
                compositeResult = compositeResult.withSideEffect(SideEffect.fixedFrom(result));
            }
        }
        if (compositeResult.isMissing()) {
            return compositeResult.asType();
        }
        return Value.of(buildEntries.apply(builder)).withSideEffect(SideEffect.fixedFrom(compositeResult));
    }

    protected ExecutionTimeValue<? extends Type> calculateExecutionTimeValue(
        Function<CollectorType, ExecutionTimeValue<? extends Type>> calculateExecutionTimeValueForCollector,
        BiFunction<List<ExecutionTimeValue<? extends Type>>, SideEffectBuilder<? extends Type>, ExecutionTimeValue<? extends Type>> calculateFixedValue,
        Function<List<Pair<CollectorType, ExecutionTimeValue<? extends Type>>>, ExecutionTimeValue<? extends Type>> calculateChangingValue
    ) {
        List<Pair<CollectorType, ExecutionTimeValue<? extends Type>>> collectorsWithValues =
            collectExecutionTimeValues(calculateExecutionTimeValueForCollector);
        if (collectorsWithValues.isEmpty()) {
            return ExecutionTimeValue.missing();
        }
        List<ExecutionTimeValue<? extends Type>> executionTimeValues = collectorsWithValues.stream().map(Pair::getRight).collect(Collectors.toList());
        ExecutionTimeValue<? extends Type> fixedOrMissing = fixedOrMissingValueOf(executionTimeValues, calculateFixedValue);
        if (fixedOrMissing != null) {
            return fixedOrMissing;
        }
        return calculateChangingValue.apply(collectorsWithValues);
    }

    // Returns an empty list when the overall value is missing.
    protected List<Pair<CollectorType, ExecutionTimeValue<? extends Type>>> collectExecutionTimeValues(Function<CollectorType, ExecutionTimeValue<? extends Type>> calculateExecutionTimeValueForCollector) {
        // These are the values that are certainly part of the result, e.g. because of absent-ignoring append/appendAll argument.
        List<Pair<CollectorType, ExecutionTimeValue<? extends Type>>> executionTimeValues = new ArrayList<>();
        // These are the values that may become part of the result if there is no missing value somewhere.
        List<Pair<CollectorType, ExecutionTimeValue<? extends Type>>> candidates = new ArrayList<>();

        // We traverse the collectors backwards (in reverse addition order) to simplify the logic and avoid processing things that are going to be discarded.
        // Because of that, values are collected in reverse order too.
        // Se also #calculatePresence.
        for (CollectorType collector : Lists.reverse(getCollectors())) {
            ExecutionTimeValue<? extends Type> result = calculateExecutionTimeValueForCollector.apply(collector);
            if (result.isMissing()) {
                // This is an add/addAll argument, but it is a missing provider.
                // Everything that was added before it isn't going to affect the result, so we stop the iteration.
                // All add/addAll that happened after it (thus already processed) but before any append/appendAll - the contents of candidates - are also discarded.
                return Lists.reverse(executionTimeValues);
            }
            if (checkAbsentIgnoring.test(collector)) {
                // This is an argument of append/appendAll. With it the property is going to be present (though maybe empty).
                // As all add/addAll arguments we've processed (thus added after this one) so far weren't missing, we're sure they'll be part of the final property's value.
                // Move them to the executionTimeValues.
                executionTimeValues.addAll(candidates);
                executionTimeValues.add(Pair.of(collector, result));
                candidates.clear();
            } else {
                // This is an argument of add/addAll that isn't definitely missing. It might be part of the final value.
                candidates.add(Pair.of(collector, result));
            }
        }
        // No missing values found, so all the candidates are part of the final value.
        executionTimeValues.addAll(candidates);
        return Lists.reverse(executionTimeValues);
    }

    @Override
    protected String toStringNoReentrance() {
        StringBuilder sb = new StringBuilder();
        getCollectors().forEach(collector -> {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(collector.toString());
        });
        return sb.toString();
    }

    protected ValueProducer getProducer(Function<CollectorType, ValueProducer> getProducerForCollector) {
        return new ValueProducer() {
            @Override
            public void visitProducerTasks(Action<? super Task> visitor) {
                getProducers(getProducerForCollector).forEach(c -> c.visitProducerTasks(visitor));
            }

            @Override
            public boolean isKnown() {
                return getProducers(getProducerForCollector).anyMatch(ValueProducer::isKnown);
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                getProducers(getProducerForCollector).forEach(c -> c.visitDependencies(context));
            }

            @Override
            public void visitContentProducerTasks(Action<? super Task> visitor) {
                getProducers(getProducerForCollector).forEach(c -> c.visitContentProducerTasks(visitor));
            }
        };
    }

    /**
     * Try to simplify the set of execution values to either a missing value or a fixed value.
     */
    @Nullable
    private ExecutionTimeValue<? extends Type> fixedOrMissingValueOf(
        List<ExecutionTimeValue<? extends Type>> values,
        BiFunction<List<ExecutionTimeValue<? extends Type>>, SideEffectBuilder<? extends Type>, ExecutionTimeValue<? extends Type>> calculateFixedExecutionTimeValue
    ) {
        boolean fixed = true;
        boolean changingContent = false;
        for (ExecutionTimeValue<? extends Type> value : values) {
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
            SideEffectBuilder<Type> sideEffectBuilder = SideEffect.builder();
            ExecutionTimeValue<? extends Type> fixedValue = calculateFixedExecutionTimeValue.apply(values, sideEffectBuilder);
            fixedValue = changingContent ? fixedValue.withChangingContent() : fixedValue;
            return fixedValue.withSideEffect(sideEffectBuilder.build());
        }
        return null;
    }

    private Stream<ValueProducer> getProducers(Function<CollectorType, ValueProducer> getProducerForCollector) {
        return getCollectors().stream().map(getProducerForCollector);
    }
}
