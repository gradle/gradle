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

package org.gradle.api.internal.changedetection.rules;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

@NonNullApi
public abstract class AbstractNamedFileSnapshotTaskStateChanges implements TaskStateChanges {
    protected final TaskExecution previous;
    protected final TaskExecution current;
    private final String title;

    protected AbstractNamedFileSnapshotTaskStateChanges(@Nullable TaskExecution previous, TaskExecution current, String title) {
        this.previous = previous;
        this.current = current;
        this.title = title;
    }

    @Nullable
    private ImmutableSortedMap<String, FileCollectionSnapshot> getPrevious() {
        return previous == null ? null : getSnapshot(previous);
    }

    private ImmutableSortedMap<String, FileCollectionSnapshot> getCurrent() {
        return getSnapshot(current);
    }

    protected abstract ImmutableSortedMap<String, FileCollectionSnapshot> getSnapshot(TaskExecution execution);

    protected Iterator<TaskStateChange> getFileChanges(final boolean includeAdded) {
        if (getPrevious() == null) {
            return Iterators.<TaskStateChange>singletonIterator(new DescriptiveChange(title + " file history is not available."));
        }
        return Iterators.concat(Iterables.transform(getCurrent().entrySet(), new Function<Map.Entry<String, FileCollectionSnapshot>, Iterator<TaskStateChange>>() {
            @Override
            public Iterator<TaskStateChange> apply(Map.Entry<String, FileCollectionSnapshot> entry) {
                String propertyName = entry.getKey();
                FileCollectionSnapshot currentSnapshot = entry.getValue();
                FileCollectionSnapshot previousSnapshot = getPrevious().get(propertyName);
                if (previousSnapshot == null) {
                    return Iterators.emptyIterator();
                }
                String propertyTitle = title + " property '" + propertyName + "'";
                return currentSnapshot.iterateContentChangesSince(previousSnapshot, propertyTitle, includeAdded);
            }
        }).iterator());
    }
}
