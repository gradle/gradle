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

/**
 * A TaskUpToDateState that remembers if it {@link #isUpToDate()} after running {@link #findChanges(org.gradle.api.Action)}.
 */
public abstract class CachingUpToDateState implements TaskUpToDateState {
    private Integer count;

    public void findChanges(final Action<? super TaskUpToDateStateChange> action) {
        // Don't re-execute if we know there are no changes
        if (count != null && count == 0) {
            return;
        }

        count = 0;
        Action<TaskUpToDateStateChange> countingAction = new Action<TaskUpToDateStateChange>() {
            public void execute(TaskUpToDateStateChange upToDateFailure) {
                action.execute(upToDateFailure);
                // Record message for later replay
                count++;
            }
        };
        doFindChanges(countingAction);
    }

    protected abstract void doFindChanges(Action<TaskUpToDateStateChange> action);

    public boolean isUpToDate() {
        if (count == null) {
            findChanges(new Action<TaskUpToDateStateChange>() {
                public void execute(TaskUpToDateStateChange action) {
                    System.out.println("SHOULD NEVER GET HERE");
                    // No-op: we just need to get the count incremented.
                }
            });
        }
        return count == 0;
    }
}
