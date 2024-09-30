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

package org.gradle.api.internal.collections;

import org.gradle.api.Action;
import org.gradle.api.internal.MutationGuard;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;

import java.util.Collection;
import java.util.Iterator;

/**
 * The heart of any domain object collection. This source is able to track eager and lazy (pending)
 * elements, and attempts to defer realization of pending elements until they are needed.
 */
public interface ElementSource<T> extends Iterable<T> {

    /**
     * Iterates over and realizes each of the elements of this source.
     */
    @Override
    Iterator<T> iterator();

    /**
     * Iterates over only the realized elements (without flushing any pending elements)
     */
    Iterator<T> iteratorNoFlush();

    /**
     * Returns true iff this source is empty.
     */
    boolean isEmpty();

    /**
     * Returns false if this source is not empty, or it is not fast to determine this.
     */
    boolean constantTimeIsEmpty();

    /**
     * Returns the number of elements in this source.
     * <p>
     * This method may be expensive, as it may require realizing all pending elements.
     */
    int size();

    /**
     * Estimate the size of this source by returning a value which is greater or equal to the real size.
     * This method is intended to be used to size temporary collections and maps to avoid resizes.
     * <p>
     * For filtered collections, this should return the size of the source collection, in order to avoid
     * performing filtering just to determine the size of temporary collections.
     */
    int estimatedSize();

    boolean contains(Object element);

    boolean containsAll(Collection<?> elements);

    boolean add(T element);

    /**
     * Sets a verifier that specifies whether there is a subscription for
     * elements of a given type. This is used to determine whether a
     * pending element should be realized upon addition to this source.
     */
    void setSubscriptionVerifier(EventSubscriptionVerifier<T> typeSubscriptions);

    void clear();

    boolean remove(Object o);

    void realizePending();

    void realizePending(Class<?> type);

    boolean addPending(ProviderInternal<? extends T> provider);

    boolean removePending(ProviderInternal<? extends T> provider);

    boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider);

    boolean removePendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider);

    void realizeExternal(ProviderInternal<? extends T> provider);

    /**
     * Adds an action to be executed when a value from a pending element is concretely added.
     * The provided action is only called when a pending value is realized and the value is not
     * already present in the concrete values of this source.
     */
    void onPendingAdded(Action<T> action);

    /**
     * Tracks whether lazy actions are currently being executed against this element source.
     */
    MutationGuard getLazyBehaviorGuard();

}
