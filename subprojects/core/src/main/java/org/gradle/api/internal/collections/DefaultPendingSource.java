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

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.internal.provider.AbstractCollectionProperty;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.DefaultSetProperty;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DefaultPendingSource<T> implements PendingSource<T> {
    private final List<CollectionProviderInternal<T, Set<T>>> pending = Lists.newArrayList();
    private Action<CollectionProviderInternal<T, Set<T>>> flushAction;

    @Override
    public void realizePending() {
        if (!pending.isEmpty()) {
            List<CollectionProviderInternal<T, Set<T>>> copied = Lists.newArrayList(pending);
            realize(copied);
        }
    }

    @Override
    public void realizePending(Class<?> type) {
        if (!pending.isEmpty()) {
            List<CollectionProviderInternal<T, Set<T>>> copied = Lists.newArrayList();
            for (CollectionProviderInternal<T, Set<T>> provider : pending) {
                if (provider.getElementType() == null || type.isAssignableFrom(provider.getElementType())) {
                    copied.add(provider);
                }
            }
            realize(copied);
        }
    }

    private void realize(Iterable<CollectionProviderInternal<T, Set<T>>> providers) {
        for (CollectionProviderInternal<T, Set<T>> collection : providers) {
            realize(collection);
        }
    }

    private void realize(CollectionProviderInternal<T, Set<T>> provider) {
        if (flushAction != null) {
            pending.remove(provider);
            flushAction.execute(provider);
        } else {
            throw new IllegalStateException("Cannot realize pending elements when realize action is not set");
        }
    }

    @Override
    public void addPendingCollection(CollectionProviderInternal<T, Set<T>> provider) {
        pending.add(provider);
    }

    @Override
    public void removePendingCollection(CollectionProviderInternal<T, Set<T>> provider) {
        pending.remove(provider);
    }

    @Override
    public void onRealize(Action<CollectionProviderInternal<T, Set<T>>> action) {
        flushAction = action;
    }

    @Override
    public void addPending(ProviderInternal<? extends T> provider) {
        pending.add(singleElementProvider(provider));
    }

    @Override
    public void removePending(ProviderInternal<? extends T> provider) {
        pending.remove(singleElementProvider(provider));
    }

    @Override
    public boolean isEmpty() {
        return pending.isEmpty();
    }

    @Override
    public int size() {
        return pending.size();
    }

    @Override
    public void clear() {
        pending.clear();
    }

    private CollectionProviderInternal<T, Set<T>> singleElementProvider(ProviderInternal<? extends T> provider) {
        ProviderInternal<T> providerInternal = Cast.uncheckedCast(provider);
        return DefaultSetProperty.from(providerInternal);
    }
}
