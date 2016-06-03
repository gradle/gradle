/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules;

import com.google.common.collect.AbstractIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation that will cache a number of results of iteration, and make them available for subsequent iterations.
 * This allows a single underlying iterator to be used for multiple iterations, up til the first iteration that exceeds the cache size.
 * When the cache is overrun, it is cleared so that any subsequent iteration will require a fresh delegate iterator.
 */
public class CachingTaskStateChanges implements TaskStateChanges {
    private final TaskStateChanges delegate;
    private final List<TaskStateChange> cache = new ArrayList<TaskStateChange>();
    private final int maxCachedChanges;
    private Iterator<TaskStateChange> delegateIterator;
    boolean overrun;

    public CachingTaskStateChanges(int maxCachedChanges, TaskStateChanges delegate) {
        this.maxCachedChanges = maxCachedChanges;
        this.delegate = delegate;
    }

    public Iterator<TaskStateChange> iterator() {
        if (delegateIterator == null || overrun) {
            reset();
        }

        return new AbstractIterator<TaskStateChange>() {
            final Iterator<TaskStateChange> cacheIterator = new ArrayList<TaskStateChange>(cache).iterator();

            @Override
            protected TaskStateChange computeNext() {
                if (cacheIterator.hasNext()) {
                    return cacheIterator.next();
                }
                if (delegateIterator.hasNext()) {
                    TaskStateChange next = delegateIterator.next();
                    maybeCache(next);
                    return next;
                }
                return endOfData();
            }
        };
    }

    private void maybeCache(TaskStateChange next) {
        if (overrun) {
            return;
        }

        if (cache.size() < maxCachedChanges) {
            cache.add(next);
        } else {
            overrun = true;
        }
    }

    private void reset() {
        cache.clear();
        delegateIterator = delegate.iterator();
        overrun = false;
    }

    public void snapshotAfterTask() {
        delegate.snapshotAfterTask();
    }
}
