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

class FileChangeCache implements Iterable<FileChange> {
    private final int maxCachedChanges;
    private final ArrayList<FileChange> cachedChanges = new ArrayList<FileChange>();
    private boolean overCapacity;
    private boolean seenAllChanges;

    FileChangeCache(int maxCachedChanges) {
        this.maxCachedChanges = maxCachedChanges;
    }

    public void cache(FileChange change) {
        seenAllChanges = false;

        if (cachedChanges.size() < maxCachedChanges) {
            cachedChanges.add(change);
            return;
        }
        // Missed cache entry: cache does not have complete set
        overCapacity = true;
    }

    public String getLastCachedChange() {
        return cachedChanges.size() == 0 ? null : cachedChanges.get(cachedChanges.size() - 1).getPath();
    }

    public Iterator<FileChange> iterator() {
        return cachedChanges.iterator();
    }

    public boolean isComplete() {
        return seenAllChanges && !overCapacity;
    }

    void hasSeenAllChanges() {
        this.seenAllChanges = true;
    }
}
