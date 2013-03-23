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

public class CompositeUpToDateState implements TaskUpToDateState {
    private final List<TaskUpToDateState> states;
    private Integer count;

    public CompositeUpToDateState(TaskUpToDateState... states) {
        this.states = Arrays.asList(states);
    }

    public void findChanges(final Action<? super TaskUpToDateStateChange> failures) {
        count = 0;
        Action<TaskUpToDateStateChange> recordingAction = new Action<TaskUpToDateStateChange>() {
            public void execute(TaskUpToDateStateChange upToDateFailure) {
                failures.execute(upToDateFailure);
                // Record message for later replay
                count++;
            }
        };
        for (TaskUpToDateState state : states) {
            state.findChanges(recordingAction);
            // Short circuit
            if (count > 0) {
                break;
            }
        }
    }

    public boolean isUpToDate() {
        if (count == null) {
            findChanges(new Action<TaskUpToDateStateChange>() {
                public void execute(TaskUpToDateStateChange failure) {
                    System.out.println("SHOULD NEVER GET HERE");
                    // No-op: we just need to get the count incremented.
                }
            });
        }
        return count == 0;
    }

    public void snapshotAfterTask() {
        for (TaskUpToDateState state : states) {
            state.snapshotAfterTask();
        }
    }
}
