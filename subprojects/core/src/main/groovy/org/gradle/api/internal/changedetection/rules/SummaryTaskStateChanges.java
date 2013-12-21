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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

class SummaryTaskStateChanges implements TaskStateChanges {
    private final int maxReportedChanges;
    private final List<TaskStateChanges> sources;


    public SummaryTaskStateChanges(int maxReportedChanges, TaskStateChanges... sources) {
        this.maxReportedChanges = maxReportedChanges;
        this.sources = Arrays.asList(sources);
    }

    /**
     * Provides an efficient summary of the changes, without doing too much unnecessary work.
     * - Will only emit changes of a single type (from a single delegate change set)
     * - Will return no more than the specified maximum of number of changes
     */
    public Iterator<TaskStateChange> iterator() {

        return new AbstractIterator<TaskStateChange>() {
            Iterator<TaskStateChange> changes;
            int count;

            @Override
            protected TaskStateChange computeNext() {
                if (changes == null) {
                    changes = firstDirtyIterator();
                }

                if (count < maxReportedChanges && changes != null && changes.hasNext()) {
                    count++;
                    return changes.next();
                }
                return endOfData();
            }
        };
    }

    private Iterator<TaskStateChange> firstDirtyIterator() {
        for (TaskStateChanges source : sources) {
            Iterator<TaskStateChange> sourceIterator = source.iterator();
            if (sourceIterator.hasNext()) {
                return sourceIterator;
            }
        }
        return null;
    }

    public void snapshotAfterTask() {
        for (TaskStateChanges state : sources) {
            state.snapshotAfterTask();
        }
    }
}
