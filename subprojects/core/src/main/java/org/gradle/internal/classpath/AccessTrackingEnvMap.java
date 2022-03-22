/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.classpath;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ForwardingMap;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A wrapper for the {@link System#getenv()} result that notifies a listener about accesses.
 */
class AccessTrackingEnvMap extends ForwardingMap<String, String> {
    private final Map<String, String> delegate;
    private final BiConsumer<? super String, ? super String> onAccess;

    public AccessTrackingEnvMap(BiConsumer<String, String> onAccess) {
        this(System.getenv(), onAccess);
    }

    @VisibleForTesting
    AccessTrackingEnvMap(Map<String, String> delegate, BiConsumer<? super String, ? super String> onAccess) {
        this.delegate = delegate;
        this.onAccess = onAccess;
    }

    @Override
    public String get(@Nullable Object key) {
        return getAndReport(key);
    }

    @Override
    public String getOrDefault(@Nullable Object key, String defaultValue) {
        String value = getAndReport(key);
        if (value == null && !delegate.containsKey(key)) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public boolean containsKey(@Nullable Object key) {
        return getAndReport(key) != null;
    }

    @Override
    public Set<String> keySet() {
        return new AccessTrackingSet<>(super.keySet(), trackingListener());
    }

    private String getAndReport(@Nullable Object key) {
        String result = delegate.get(key);
        // The delegate will throw if something that isn't a string is used there. Do call delegate.get() first so the exception is thrown form the JDK code to avoid extra blame.
        onAccess.accept((String) key, result);
        return result;
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return new AccessTrackingSet<>(delegate.entrySet(), entrySetTrackingListener());
    }

    private void onAccessEntrySetElement(@Nullable Object potentialEntry) {
        Map.Entry<String, String> entry = AccessTrackingUtils.tryConvertingToTrackableEntry(potentialEntry);
        if (entry != null) {
            getAndReport(entry.getKey());
        }
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super String> action) {
        reportAggregatingAccess();
        delegate.forEach(action);
    }

    @Override
    public int size() {
        reportAggregatingAccess();
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        reportAggregatingAccess();
        return delegate.isEmpty();
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(@Nullable Object object) {
        reportAggregatingAccess();
        return delegate.equals(object);
    }

    @Override
    public int hashCode() {
        reportAggregatingAccess();
        return delegate.hashCode();
    }

    @Override
    protected Map<String, String> delegate() {
        return delegate;
    }

    private void reportAggregatingAccess() {
        // Mark all map contents as inputs if some aggregating access is used.
        delegate.forEach(onAccess);
    }

    private AccessTrackingSet.Listener trackingListener() {
        return new AccessTrackingSet.Listener() {
            @Override
            public void onAccess(Object o) {
                getAndReport(o);
            }

            @Override
            public void onAggregatingAccess() {
                reportAggregatingAccess();
            }

            @Override
            public void onRemove(Object object) {
                // Environment variables are immutable.
            }

            @Override
            public void onClear() {
                // Environment variables are immutable.
            }
        };
    }

    private AccessTrackingSet.Listener entrySetTrackingListener() {
        return new AccessTrackingSet.Listener() {
            @Override
            public void onAccess(Object o) {
                onAccessEntrySetElement(o);
            }

            @Override
            public void onAggregatingAccess() {
                reportAggregatingAccess();
            }

            @Override
            public void onRemove(Object object) {
                // Environment variables are immutable.
            }

            @Override
            public void onClear() {
                // Environment variables are immutable.
            }
        };
    }
}

