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

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.execution.history.BeforeExecutionState;

/**
 * Represents the complete changes in execution state
 */
public interface ExecutionStateChanges {

    /**
     * Returns all change messages for inputs and outputs.
     */
    ImmutableList<String> getChangeDescriptions();

    InputChangesInternal createInputChanges();

    BeforeExecutionState getBeforeExecutionState();

    static ExecutionStateChanges incremental(
        ImmutableList<String> changeDescriptions,
        BeforeExecutionState beforeExecutionState,
        InputFileChanges inputFileChanges,
        IncrementalInputProperties incrementalInputProperties
    ) {
        return new ExecutionStateChanges() {
            @Override
            public ImmutableList<String> getChangeDescriptions() {
                return changeDescriptions;
            }

            @Override
            public InputChangesInternal createInputChanges() {
                return new IncrementalInputChanges(inputFileChanges, incrementalInputProperties);
            }

            @Override
            public BeforeExecutionState getBeforeExecutionState() {
                return beforeExecutionState;
            }
        };
    }

    static ExecutionStateChanges nonIncremental(
        ImmutableList<String> changeDescriptions,
        BeforeExecutionState beforeExecutionState,
        IncrementalInputProperties incrementalInputProperties
    ) {
        return new ExecutionStateChanges() {
            @Override
            public ImmutableList<String> getChangeDescriptions() {
                return changeDescriptions;
            }

            @Override
            public InputChangesInternal createInputChanges() {
                return new NonIncrementalInputChanges(beforeExecutionState.getInputFileProperties(), incrementalInputProperties);
            }

            @Override
            public BeforeExecutionState getBeforeExecutionState() {
                return beforeExecutionState;
            }
        };
    }
}
