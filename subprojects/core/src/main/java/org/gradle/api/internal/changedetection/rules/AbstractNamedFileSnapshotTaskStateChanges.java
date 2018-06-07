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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterators;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@NonNullApi
public abstract class AbstractNamedFileSnapshotTaskStateChanges implements TaskStateChanges, Iterable<TaskStateChange> {
    protected final TaskExecution previous;
    protected final TaskExecution current;
    private final String title;

    protected AbstractNamedFileSnapshotTaskStateChanges(TaskExecution previous, TaskExecution current, String title) {
        this.previous = previous;
        this.current = current;
        this.title = title;
    }

    private ImmutableSortedMap<String, FileCollectionSnapshot> getPrevious() {
        return getSnapshot(previous);
    }

    private ImmutableSortedMap<String, FileCollectionSnapshot> getCurrent() {
        return getSnapshot(current);
    }

    protected abstract ImmutableSortedMap<String, FileCollectionSnapshot> getSnapshot(TaskExecution execution);

    protected Iterator<TaskStateChange> getFileChanges(final boolean includeAdded) {
        final List<Iterator<TaskStateChange>> iterators = new ArrayList<Iterator<TaskStateChange>>();
        SortedMapDiffUtil.diff(getPrevious(), getCurrent(), new PropertyDiffListener<String, FileCollectionSnapshot>() {
            @Override
            public void removed(String previousProperty) {
            }

            @Override
            public void added(String currentProperty) {
            }

            @Override
            public void updated(String property, FileCollectionSnapshot previousSnapshot, FileCollectionSnapshot currentSnapshot) {
                String propertyTitle = title + " property '" + property + "'";
                iterators.add(currentSnapshot.iterateContentChangesSince(previousSnapshot, propertyTitle, includeAdded));
            }
        });

        return Iterators.concat(iterators.iterator());
    }

    @Override
    public boolean accept(TaskStateChangeVisitor visitor) {
        for (TaskStateChange taskStateChange : this) {
            if (!visitor.visitChange(taskStateChange)) {
                return false;
            }
        }
        return true;
    }
}
