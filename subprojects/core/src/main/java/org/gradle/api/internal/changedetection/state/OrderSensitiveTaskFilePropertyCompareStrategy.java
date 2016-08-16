/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.AbstractIterator;
import org.gradle.api.internal.changedetection.rules.ChangeType;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

import java.util.Iterator;
import java.util.Map;

class OrderSensitiveTaskFilePropertyCompareStrategy implements TaskFilePropertyCompareStrategy {

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(Map<String, IncrementalFileSnapshot> current, Map<String, IncrementalFileSnapshot> previous, final String fileType) {
        final Iterator<Map.Entry<String, IncrementalFileSnapshot>> currentEntries = current.entrySet().iterator();
        final Iterator<Map.Entry<String, IncrementalFileSnapshot>> previousEntries = previous.entrySet().iterator();
        return new AbstractIterator<TaskStateChange>() {
            @Override
            protected TaskStateChange computeNext() {
                while (true) {
                    if (currentEntries.hasNext()) {
                        Map.Entry<String, IncrementalFileSnapshot> current = currentEntries.next();
                        if (previousEntries.hasNext()) {
                            Map.Entry<String, IncrementalFileSnapshot> other = previousEntries.next();
                            if (current.getKey().equals(other.getKey())) {
                                if (current.getValue().isContentUpToDate(other.getValue())) {
                                    continue;
                                } else {
                                    return new FileChange(current.getKey(), ChangeType.MODIFIED, fileType);
                                }
                            } else {
                                return new FileChange(current.getKey(), ChangeType.REPLACED, fileType);
                            }
                        } else {
                            return new FileChange(current.getKey(), ChangeType.ADDED, fileType);
                        }
                    } else {
                        if (previousEntries.hasNext()) {
                            return new FileChange(previousEntries.next().getKey(), ChangeType.REMOVED, fileType);
                        } else {
                            return endOfData();
                        }
                    }
                }
            }
        };
    }

    @Override
    public void appendToCacheKey(TaskCacheKeyBuilder builder, Map<String, IncrementalFileSnapshot> snapshots) {
        for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
            builder.putString(entry.getKey());
            builder.putBytes(entry.getValue().getHash().asBytes());
        }
    }
}
