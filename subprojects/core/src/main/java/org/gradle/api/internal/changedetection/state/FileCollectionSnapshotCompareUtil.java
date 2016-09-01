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

import com.google.common.collect.Iterators;
import org.gradle.api.internal.changedetection.rules.ChangeType;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

class FileCollectionSnapshotCompareUtil {
    /**
     * Compares snapshot collections if both current and previous states have at most one element.
     *
     * @param current the current state of the snapshot.
     * @param previous the previous state of the snapshot.
     * @param fileType the file type to use when creating the {@link FileChange}.
     * @param includeAdded    whether or not to include added files.
     * @return either a single change representing the change that happened,
     * or {@code null} if there are more than one element in either {@code current}
     * or {@code previous}.
     */
    public static Iterator<TaskStateChange> compareTrivialSnapshots(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String fileType, boolean includeAdded) {
        TaskStateChange change;
        switch (current.size()) {
            case 0:
                switch (previous.size()) {
                    case 0:
                        change = null;
                        break;

                    case 1:
                        String path = previous.keySet().iterator().next();
                        change = new FileChange(path, ChangeType.REMOVED, fileType);
                        break;

                    default:
                        return null;
                }
                break;

            case 1:
                switch (previous.size()) {
                    case 0:
                        if (includeAdded) {
                            String path = current.keySet().iterator().next();
                            change = new FileChange(path, ChangeType.ADDED, fileType);
                        } else {
                            change = null;
                        }
                        break;
                    case 1:
                        Entry<String, NormalizedFileSnapshot> previousEntry = previous.entrySet().iterator().next();
                        Entry<String, NormalizedFileSnapshot> currentEntry = current.entrySet().iterator().next();
                        NormalizedFileSnapshot normalizedPrevious = previousEntry.getValue();
                        NormalizedFileSnapshot normalizedCurrent = currentEntry.getValue();
                        if (normalizedCurrent.getNormalizedPath().equals(normalizedPrevious.getNormalizedPath())) {
                            IncrementalFileSnapshot previousSnapshot = normalizedPrevious.getSnapshot();
                            IncrementalFileSnapshot currentSnapshot = normalizedCurrent.getSnapshot();
                            if (!currentSnapshot.isContentUpToDate(previousSnapshot)) {
                                String path = currentEntry.getKey();
                                change = new FileChange(path, ChangeType.MODIFIED, fileType);
                            } else {
                                change = null;
                            }
                        } else {
                            if (includeAdded) {
                                String path = currentEntry.getKey();
                                change = new FileChange(path, ChangeType.REPLACED, fileType);
                            } else {
                                String path = previousEntry.getKey();
                                change = new FileChange(path, ChangeType.REMOVED, fileType);
                            }
                        }
                        break;
                    default:
                        return null;
                }
                break;

            default:
                return null;
        }
        if (change == null) {
            return Iterators.emptyIterator();
        } else {
            return Iterators.singletonIterator(change);
        }
    }
}
