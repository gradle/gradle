/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.TaskInternal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CompositeUpToDateRule implements UpToDateRule {
    private final List<UpToDateRule> rules;

    public CompositeUpToDateRule(UpToDateRule... rules) {
        this.rules = new ArrayList<UpToDateRule>(Arrays.asList(rules));
    }

    public TaskUpToDateState create(TaskInternal task, TaskExecution previousExecution, TaskExecution currentExecution) {
        final List<TaskUpToDateState> states = new ArrayList<TaskUpToDateState>();
        for (UpToDateRule rule : rules) {
            states.add(rule.create(task, previousExecution, currentExecution));
        }
        return new TaskUpToDateState() {
            public void checkUpToDate(Collection<String> messages) {
                for (int i = 0; messages.isEmpty() && i < states.size(); i++) {
                    TaskUpToDateState state = states.get(i);
                    state.checkUpToDate(messages);
                }
            }

            public void snapshotAfterTask() {
                for (TaskUpToDateState state : states) {
                    state.snapshotAfterTask();
                }
            }
        };
    }
}
