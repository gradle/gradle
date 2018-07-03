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
import org.gradle.api.internal.provider.ProviderInternal;

import java.util.List;

public class DefaultPendingSource<T> implements PendingSource<T> {
    private final List<ProviderInternal<? extends T>> pending = Lists.newArrayList();
    private Action<ProviderInternal<? extends T>> flushAction;

    @Override
    public void realizePending() {
        if (!pending.isEmpty()) {
            List<ProviderInternal<? extends T>> copied = Lists.newArrayList(pending);
            realize(copied);
        }
    }

    @Override
    public void realizePending(Class<?> type) {
        if (!pending.isEmpty()) {
            List<ProviderInternal<? extends T>> copied = Lists.newArrayList();
            for (ProviderInternal<? extends T> provider : pending) {
                if (provider.getType() == null || type.isAssignableFrom(provider.getType())) {
                    copied.add(provider);
                }
            }
            realize(copied);
        }
    }

    private void realize(Iterable<ProviderInternal<? extends T>> elements) {
        for (ProviderInternal<? extends T> provider : elements) {
            realize(provider);
        }
    }

    private void realize(ProviderInternal<? extends T> provider) {
        if (flushAction != null) {
            pending.remove(provider);
            flushAction.execute(provider);
        } else {
            throw new IllegalStateException("Cannot realize pending elements when realize action is not set");
        }
    }

    @Override
    public void addPending(ProviderInternal<? extends T> provider) {
        pending.add(provider);
    }

    @Override
    public void removePending(ProviderInternal<? extends T> provider) {
        pending.remove(provider);
    }

    @Override
    public void onRealize(Action<ProviderInternal<? extends T>> action) {
        this.flushAction = action;
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
}
