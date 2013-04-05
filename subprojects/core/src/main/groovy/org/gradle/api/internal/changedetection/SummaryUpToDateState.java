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

package org.gradle.api.internal.changedetection;

import org.gradle.api.Action;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// TODO:DAZ Unit Test
public class SummaryUpToDateState {
    private final int maxReportedChanges;
    private final List<TaskUpToDateState> sources;


    public SummaryUpToDateState(int maxReportedChanges, TaskUpToDateState... sources) {
        this.maxReportedChanges = maxReportedChanges;
        this.sources = Arrays.asList(sources);
    }

    /*
     * Provides an efficient summary of the changes, without doing too much unnecessary work.
     * - Will only emit changes of a single type (from a single delegate change set)
     * - Will return no more than the specified maximum of number of changes
     */
    public void findChanges(final Action<TaskUpToDateChange> listener) {
        SummaryListener summaryListener = new SummaryListener(listener, maxReportedChanges);
        for (TaskUpToDateState source : sources) {
            source.findChanges(summaryListener);

            // Don't check any more states once a change is detected
            if (summaryListener.changeCount > 0) {
                break;
            }
        }
    }

    public boolean hasChanges() {
        final AtomicBoolean hasChanges = new AtomicBoolean(false);
        findChanges(new Action<TaskUpToDateChange>() {
            public void execute(TaskUpToDateChange taskUpToDateChange) {
                hasChanges.set(true);
            }
        });
        return hasChanges.get();
    }

    public void snapshotAfterTask() {
        for (TaskUpToDateState state : sources) {
            state.snapshotAfterTask();
        }
    }

    private class SummaryListener implements UpToDateChangeListener {
        private final Action<TaskUpToDateChange> action;
        private final int maxReportedChanges;
        int changeCount;

        public SummaryListener(Action<TaskUpToDateChange> action, int maxReportedChanges) {
            this.action = action;
            this.maxReportedChanges = maxReportedChanges;
        }

        public void accept(TaskUpToDateChange change) {
            changeCount++;
            action.execute(change);
        }

        public boolean isAccepting() {
            return changeCount < maxReportedChanges;
        }
    }
}
