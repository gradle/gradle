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
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;

public interface PendingSource<T> {
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

    boolean isEmpty();

    int size();

    void clear();
}
