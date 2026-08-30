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


/**
 * A collector is a value supplier of zero or more values of type {@link T}.
 * <p>
 *     A <code>Collector</code> represents an increment to a collection property.
 * </p>
 */
public interface Collector<T> extends ValueSupplier {
    Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest);

    int size();

    /**
     * A lower bound on {@link #size()} that is cheap to compute.
     * <p>
     * Unlike {@code size()}, this never evaluates a provider - {@code ElementsFromCollectionProvider.size()}
     * realises the whole upstream value, which would defeat the point of asking. Used only to presize the
     * builder, so an underestimate costs a resize and nothing else.
     */
    default int sizeHint() {
        return 0;
    }

    ExecutionTimeValue<? extends Iterable<? extends T>> calculateExecutionTimeValue();
}
