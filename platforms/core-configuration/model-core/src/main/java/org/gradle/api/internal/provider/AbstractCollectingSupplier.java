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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Task;
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

public abstract class AbstractCollectingSupplier<COLLECTOR extends ValueSupplier, TYPE> extends AbstractMinimalProvider<TYPE> {
    private final Predicate<COLLECTOR> checkAbsentIgnoring;
    // This list is shared by the collectors produced by `plus`, so we don't have to copy the collectors every time.
    // However, this also means that you can only call plus on a given collector once.
    protected final ArrayList<COLLECTOR> collectors; // TODO - Replace with PersistentList? This may make value calculation inefficient because the PersistentList can only prepend to head.
    protected final int size;

    public AbstractCollectingSupplier(
        Predicate<COLLECTOR> checkAbsentIgnoring,
        @SuppressWarnings("NonApiType") ArrayList<COLLECTOR> collectors,
        int size
    ) {
        this.collectors = collectors;
        this.size = size;
        this.checkAbsentIgnoring = checkAbsentIgnoring;
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

    protected List<COLLECTOR> getCollectors() {
        return collectors.subList(0, size);
    }

    protected boolean calculatePresence(Predicate<COLLECTOR> calculatePresenceForCollector) {
        // We're traversing the elements in reverse addition order.
        // When determining the presence of the value, the last argument wins.
        // See also #collectExecutionTimeValues().
        for (COLLECTOR collector : Lists.reverse(getCollectors())) {
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

    protected <ENTRIES, BUILDER> Value<ENTRIES> calculateValue(
        BiFunction<BUILDER, COLLECTOR, Value<Void>> collectEntriesForCollector,
        Supplier<BUILDER> supplyBuilder,
        Function<BUILDER, ENTRIES> buildEntries
    ) {
        BUILDER builder = supplyBuilder.get();
        Value<Void> compositeResult = Value.present();
        for (COLLECTOR collector : getCollectors()) {
            if (compositeResult.isMissing() && !checkAbsentIgnoring.test(collector)) {
                // The property is missing so far and the argument is of add/addAll.
                // The property is going to be missing regardless of its value.
                continue;
            }
            Value<Void> result = collectEntriesForCollector.apply(builder, collector);
            if (result.isMissing()) {
                // This is the argument of add/addAll and it is missing. It "poisons" the property (it becomes missing).
                // We discard all values and side effects gathered so far.
                builder = supplyBuilder.get();
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

    protected ExecutionTimeValue<? extends TYPE> calculateExecutionTimeValue(
        Function<COLLECTOR, ExecutionTimeValue<? extends TYPE>> calculateExecutionTimeValueForCollector,
        BiFunction<List<ExecutionTimeValue<? extends TYPE>>, SideEffectBuilder<TYPE>, ExecutionTimeValue<? extends TYPE>> calculateFixedExecutionTimeValue,
        Function<List<Pair<COLLECTOR, ExecutionTimeValue<? extends TYPE>>>, ExecutionTimeValue<? extends TYPE>> calculateChangingExecutionTimeValue
    ) {
        List<Pair<COLLECTOR, ExecutionTimeValue<? extends TYPE>>> collectorsWithValues =
            collectExecutionTimeValues(calculateExecutionTimeValueForCollector);
        if (collectorsWithValues.isEmpty()) {
            return ExecutionTimeValue.missing();
        }
        List<ExecutionTimeValue<? extends TYPE>> executionTimeValues = collectorsWithValues.stream().map(Pair::getRight).collect(Collectors.toList());
        ExecutionTimeValue<? extends TYPE> fixedOrMissing = fixedOrMissingValueOf(executionTimeValues, calculateFixedExecutionTimeValue);
        if (fixedOrMissing != null) {
            return fixedOrMissing;
        }
        return calculateChangingExecutionTimeValue.apply(collectorsWithValues);
    }

    /**
     * Returns an empty list when the overall value is missing.
     */
    protected List<Pair<COLLECTOR, ExecutionTimeValue<? extends TYPE>>> collectExecutionTimeValues(Function<COLLECTOR, ExecutionTimeValue<? extends TYPE>> calculateExecutionTimeValueForCollector) {
        // These are the values that are certainly part of the result, e.g. because of absent-ignoring append/appendAll argument.
        List<Pair<COLLECTOR, ExecutionTimeValue<? extends TYPE>>> executionTimeValues = new ArrayList<>();
        // These are the values that may become part of the result if there is no missing value somewhere.
        List<Pair<COLLECTOR, ExecutionTimeValue<? extends TYPE>>> candidates = new ArrayList<>();

        // We traverse the collectors backwards (in reverse addition order) to simplify the logic and avoid processing things that are going to be discarded.
        // Because of that, values are collected in reverse order too.
        // Se also #calculatePresence.
        for (COLLECTOR collector : Lists.reverse(getCollectors())) {
            ExecutionTimeValue<? extends TYPE> result = calculateExecutionTimeValueForCollector.apply(collector);
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
        return getCollectors().stream().map(ValueSupplier::getProducer);
    }
}
