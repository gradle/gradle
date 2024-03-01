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

import java.util.Collection;

/**
 * A {@link ValueSupplier value supplier} that supplies collection values. Instances of
 * this type are used as value suppliers for {@link AbstractCollectionProperty}.
 *
 * @param <T> the type of elements
 * @param <C> type of collection
 */
interface CollectionSupplier<T, C extends Collection<? extends T>> extends ValueSupplier {
    /**
     * Realizes and returns the (collection) value.
     */
    Value<? extends C> calculateValue(ValueConsumer consumer);

    /**
     * Combines the current collection supplier with the given {@link Collector} (also a {@link ValueSupplier value supplier}), producing a new
     * collection supplier that produces a collection that contains the original elements and the
     * elements supplied by the given collector.
     *
     * @param added a collector that represents an addition to the collection to be returned by this supplier
     * @return a new supplier that produces a collection that contains the
     * same elements as this supplier, plus the elements obtained via the given <code>added</code> collector
     */
    CollectionSupplier<T, C> plus(Collector<T> added);

    ExecutionTimeValue<? extends C> calculateExecutionTimeValue();

    /**
     * Returns a view of this supplier that will calculate its value as empty if it would be missing.
     * If this supplier already ignores absent results, returns this supplier.
     */
    CollectionSupplier<T, C> absentIgnoring();

    /**
     * Returns a view of this supplier that will calculate its value as empty if it would be missing,
     * if required. If not required, or this supplier already ignores absent results, returns this supplier.
     */
    default CollectionSupplier<T, C> absentIgnoringIfNeeded(boolean required) {
        return required ? absentIgnoring() : this;
    }
}
