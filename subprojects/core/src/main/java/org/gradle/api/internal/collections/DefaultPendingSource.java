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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.Collectors.*;
import org.gradle.api.internal.provider.ProviderInternal;

import java.util.Iterator;
import java.util.List;

public class DefaultPendingSource<T> implements PendingSource<T> {
    private final List<TypedCollector<T>> pending = Lists.newArrayList();
    private Action<T> flushAction;

    @Override
    public void realizePending() {
        if (!pending.isEmpty()) {
            List<TypedCollector<T>> copied = Lists.newArrayList(pending);
            realize(copied);
        }
    }

    @Override
    public void realizePending(Class<?> type) {
        if (!pending.isEmpty()) {
            List<TypedCollector<T>> copied = Lists.newArrayList();
            for (TypedCollector<T> collector : pending) {
                if (collector.getType() == null || type.isAssignableFrom(collector.getType())) {
                    copied.add(collector);
                }
            }
            realize(copied);
        }
    }

    private void realize(Iterable<TypedCollector<T>> collectors) {
        for (TypedCollector<T> collector : collectors) {
            if (flushAction != null) {
                pending.remove(collector);
                List<T> realized = Lists.newArrayList();
                collector.collectInto(realized);
                for (T element : realized) {
                    flushAction.execute(element);
                }
            } else {
                throw new IllegalStateException("Cannot realize pending elements when realize action is not set");
            }
        }
    }

    @Override
    public boolean addPending(ProviderInternal<? extends T> provider) {
        return pending.add(new TypedCollector<T>(provider.getType(), new ElementFromProvider<T>(provider)));
    }

    @Override
    public boolean removePending(ProviderInternal<? extends T> provider) {
        return removeByProvider(provider);
    }

    private boolean removeByProvider(ProviderInternal<?> provider) {
        Iterator<TypedCollector<T>> iterator = pending.iterator();
        while (iterator.hasNext()) {
            TypedCollector<T> collector = iterator.next();
            if (collector.isProvidedBy(provider)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        return pending.add(new TypedCollector<T>(provider.getElementType(), new ElementsFromCollectionProvider<T>(provider)));
    }

    @Override
    public boolean removePendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        return removeByProvider(provider);
    }

    @Override
    public void onRealize(Action<T> action) {
        this.flushAction = action;
    }

    @Override
    public void realizeExternal(ProviderInternal<? extends T> provider) {
        removePending(provider);
    }

    @Override
    public boolean isEmpty() {
        return pending.isEmpty();
    }

    @Override
    public int size() {
        int count = 0;
        for (TypedCollector<T> collector : pending) {
            count += collector.size();
        }
        return count;
    }

    @Override
    public void clear() {
        pending.clear();
    }
}
