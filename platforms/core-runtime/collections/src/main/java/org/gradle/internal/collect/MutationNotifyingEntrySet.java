/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.collect;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * A {@link Map#entrySet()} wrapper that, in addition to the mutators guarded by
 * {@link MutationNotifyingSet}, guards {@link Map.Entry#setValue(Object)} on entries handed out
 * via {@link #iterator()}, {@link #forEach(Consumer)} and {@link #spliterator()} (and therefore streams).
 *
 * <p>{@link #toArray()} intentionally returns the live entries unwrapped; mutating the backing map
 * through {@code setValue} on an entry obtained from the array is not detected.
 */
class MutationNotifyingEntrySet<K, V> extends MutationNotifyingSet<Map.Entry<K, V>> {

    MutationNotifyingEntrySet(Set<Map.Entry<K, V>> delegate, Consumer<String> onMutation) {
        super(delegate, onMutation);
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        // super.iterator() already notifies on remove(); wrap the entries it yields to guard setValue().
        final Iterator<Map.Entry<K, V>> it = super.iterator();
        return new Iterator<Map.Entry<K, V>>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Map.Entry<K, V> next() {
                return new MutationNotifyingEntry<K, V>(it.next(), onMutation);
            }

            @Override
            public void remove() {
                // notification already emitted by the iterator from super.iterator().
                it.remove();
            }
        };
    }

    @Override
    public void forEach(Consumer<? super Map.Entry<K, V>> action) {
        delegate.forEach(entry -> action.accept(new MutationNotifyingEntry<K, V>(entry, onMutation)));
    }

    @Override
    public Spliterator<Map.Entry<K, V>> spliterator() {
        return Spliterators.spliterator(iterator(), size(), Spliterator.DISTINCT);
    }
}
