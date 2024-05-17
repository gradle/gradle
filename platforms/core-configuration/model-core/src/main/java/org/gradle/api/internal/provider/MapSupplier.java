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

import java.util.Map;
import java.util.Set;

/**
 * <code>MapSupplier</code> is for {@link DefaultMapProperty} what {@link CollectionSupplier} is for {@link AbstractCollectionProperty}.
 *
 * @param <K> the type of map entry key
 * @param <V> the type of map entry value
 */
interface MapSupplier<K, V> extends ValueSupplier {
    Value<? extends Map<K, V>> calculateValue(ValueConsumer consumer);

    Value<? extends Set<K>> calculateKeys(ValueConsumer consumer);

    MapSupplier<K, V> plus(MapCollector<K, V> collector);

    /**
     * Returns a view of this supplier that may calculate its value as empty if it would be missing.
     */
    MapSupplier<K, V> absentIgnoring();

    default MapSupplier<K, V> absentIgnoringIfNeeded(boolean required) {
        return required ? absentIgnoring() : this;
    }

    ExecutionTimeValue<? extends Map<K, V>> calculateExecutionTimeValue();
}
