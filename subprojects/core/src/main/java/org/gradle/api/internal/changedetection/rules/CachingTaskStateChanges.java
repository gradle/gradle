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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation that will cache a number of results of iteration, and make them available for subsequent iterations.
 * This allows a single underlying iterator to be used for multiple iterations, up til the first iteration that exceeds the cache size.
 * When the cache is overrun, it is cleared so that any subsequent iteration will require a fresh delegate iterator.
 */
public class CachingTaskStateChanges implements TaskStateChanges {
    private final Iterable<TaskStateChange> delegate;
    private final List<TaskStateChange> cache = new ArrayList<TaskStateChange>();
    private final int maxCachedChanges;
    private Iterator<TaskStateChange> delegateIterator;
    private boolean overrun;

    public CachingTaskStateChanges(int maxCachedChanges, Iterable<TaskStateChange> delegate) {
        this.maxCachedChanges = maxCachedChanges;
        this.delegate = delegate;
    }

    @Override
    public boolean accept(TaskStateChangeVisitor visitor) {
        if (delegateIterator == null || overrun) {
            reset();
        }

        for (TaskStateChange change : cache) {
            if (!visitor.visitChange(change)) {
                return false;
            }
        }
        while (delegateIterator.hasNext()) {
            TaskStateChange next = delegateIterator.next();
            maybeCache(next);
            if (!visitor.visitChange(next)) {
                return false;
            }
        }
        return true;
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
}
