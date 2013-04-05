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

import java.util.Arrays;
import java.util.List;

// TODO:DAZ Unit Test
class SummaryTaskStateChanges implements TaskStateChanges {
    private final int maxReportedChanges;
    private final List<TaskStateChanges> sources;


    public SummaryTaskStateChanges(int maxReportedChanges, TaskStateChanges... sources) {
        this.maxReportedChanges = maxReportedChanges;
        this.sources = Arrays.asList(sources);
    }

    /*
     * Provides an efficient summary of the changes, without doing too much unnecessary work.
     * - Will only emit changes of a single type (from a single delegate change set)
     * - Will return no more than the specified maximum of number of changes
     */
    public void findChanges(UpToDateChangeListener listener) {
        SummaryListener summaryListener = new SummaryListener(listener, maxReportedChanges);
        for (TaskStateChanges source : sources) {
            source.findChanges(summaryListener);

            // Don't check any more states once a change is detected
            if (summaryListener.changeCount > 0) {
                break;
            }
        }
    }

    public void snapshotAfterTask() {
        for (TaskStateChanges state : sources) {
            state.snapshotAfterTask();
        }
    }

    private class SummaryListener implements UpToDateChangeListener {
        private final UpToDateChangeListener delegate;
        private final int maxReportedChanges;
        int changeCount;

        public SummaryListener(UpToDateChangeListener delegate, int maxReportedChanges) {
            this.delegate = delegate;
            this.maxReportedChanges = maxReportedChanges;
        }

        public void accept(TaskStateChange change) {
            changeCount++;
            delegate.accept(change);
        }

        public boolean isAccepting() {
            return changeCount < maxReportedChanges && delegate.isAccepting();
        }
    }
}
