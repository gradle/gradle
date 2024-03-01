/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.ImmutableCollection;
import org.gradle.api.Action;


/**
 * A collector is a value supplier of zero or more values of type {@link T}.
 * <p>
 *     A <code>Collector</code> represents an increment to a collection property.
 * </p>
 */
public interface Collector<T> extends ValueSupplier {
    Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest);

    int size();

    void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor);

    /**
     * Returns a view of this collector that never returns a missing value.
     */
    Collector<T> absentIgnoring();

    /**
     * Convenience method that returns a view of this collector that never returns a missing value,
     * if that capability is required, or this very collector.
     */
    default Collector<T> absentIgnoringIfNeeded(boolean required) {
        return required ? absentIgnoring() : this;
    }
}
