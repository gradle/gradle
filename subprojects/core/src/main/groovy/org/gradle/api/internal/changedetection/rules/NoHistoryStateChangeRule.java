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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import java.util.List;

class NoHistoryStateChangeRule {
    public static TaskStateChanges create(final TaskInternal task, final TaskExecution previousExecution) {
        return new SimpleTaskStateChanges() {
            @Override
            protected void addAllChanges(List<TaskStateChange> changes) {
                if (previousExecution == null) {
                    changes.add(new DescriptiveChange("No history is available."));
                }
            }
        };
    }
}
