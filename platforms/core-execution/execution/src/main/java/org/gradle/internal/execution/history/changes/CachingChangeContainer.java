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

package org.gradle.internal.execution.history.changes;

import java.util.ArrayList;
import java.util.List;

/**
 * An implementation that will cache a number of results of visiting, and make them available for subsequent visits.
 * When the cache is overrun, then the delegate will be visited directly in further requests.
 * If a visitor does not consume all the changes, then nothing is cached.
 */
public class CachingChangeContainer implements ChangeContainer {
    private final ChangeContainer delegate;
    private final List<Change> cache = new ArrayList<Change>();
    private final int maxCachedChanges;
    private boolean cached;
    private boolean overrun;

    public CachingChangeContainer(int maxCachedChanges, ChangeContainer delegate) {
        this.maxCachedChanges = maxCachedChanges;
        this.delegate = delegate;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        if (overrun) {
            return delegate.accept(visitor);
        }
        if (cached) {
            for (Change change : cache) {
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

    private class CachingVisitor implements ChangeVisitor {
        private final ChangeVisitor delegate;
        private int numChanges;

        public CachingVisitor(ChangeVisitor delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean visitChange(Change change) {
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
