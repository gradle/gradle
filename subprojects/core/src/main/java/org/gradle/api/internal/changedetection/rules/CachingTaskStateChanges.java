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

import org.gradle.internal.changes.TaskStateChange;
import org.gradle.internal.changes.TaskStateChangeVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * An implementation that will cache a number of results of visiting, and make them available for subsequent visits.
 * When the cache is overrun, then the delegate will be visited directly in further requests.
 * If a visitor does not consume all the changes, then nothing is cached.
 */
public class CachingTaskStateChanges implements TaskStateChanges {
    private final TaskStateChanges delegate;
    private final List<TaskStateChange> cache = new ArrayList<TaskStateChange>();
    private final int maxCachedChanges;
    private boolean cached;
    private boolean overrun;

    public CachingTaskStateChanges(int maxCachedChanges, TaskStateChanges delegate) {
        this.maxCachedChanges = maxCachedChanges;
        this.delegate = delegate;
    }

    @Override
    public boolean accept(TaskStateChangeVisitor visitor) {
        if (overrun) {
            return delegate.accept(visitor);
        }
        if (cached) {
            for (TaskStateChange change : cache) {
                if (!visitor.visitChange(change)) {
                    return false;
                }
            }
            return true;
        }
        cache.clear();
        boolean acceptedAllChanges = delegate.accept(new CachingVisitor(visitor));
        cached = acceptedAllChanges && !overrun;
        return acceptedAllChanges;
    }

    private class CachingVisitor implements TaskStateChangeVisitor {
        private final TaskStateChangeVisitor delegate;
        private int numChanges;

        public CachingVisitor(TaskStateChangeVisitor delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean visitChange(TaskStateChange change) {
            if (numChanges < maxCachedChanges) {
                numChanges++;
                cache.add(change);
            } else {
                overrun = true;
                cache.clear();
            }
            return delegate.visitChange(change);
        }
    }
}
