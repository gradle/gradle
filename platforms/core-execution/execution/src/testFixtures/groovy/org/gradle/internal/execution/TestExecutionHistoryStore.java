/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultPreviousExecutionState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public class TestExecutionHistoryStore implements ExecutionHistoryStore {

    private final Map<String, PreviousExecutionState> executionHistory = new HashMap<>();

    @Override
    public Optional<PreviousExecutionState> load(String key) {
        return Optional.ofNullable(executionHistory.get(key));
    }

    @Override
    public void store(String key, AfterExecutionState executionState) {
        executionHistory.put(key, DefaultPreviousExecutionState.from(executionState));
    }

    @Override
    public void remove(String key) {
        executionHistory.remove(key);
    }

    public Map<String, PreviousExecutionState> getExecutionHistory() {
        return executionHistory;
    }
}
