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
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

/**
 * Represents the complete changes in execution state
 */
public interface ExecutionStateChanges {

    /**
     * Returns all change messages for inputs and outputs.
     */
    ImmutableList<String> getAllChangeMessages();

    InputChangesInternal createInputChanges();

    static ExecutionStateChanges incremental(
        ImmutableList<String> allChangeMessages,
        InputFileChanges inputFileChanges,
        IncrementalInputProperties incrementalInputProperties
    ) {
        return new ExecutionStateChanges() {
            @Override
            public ImmutableList<String> getAllChangeMessages() {
                return allChangeMessages;
            }

            @Override
            public InputChangesInternal createInputChanges() {
                return new IncrementalInputChanges(inputFileChanges, incrementalInputProperties);
            }
        };
    }

    static ExecutionStateChanges nonIncremental(
        ImmutableList<String> allChangeMessages,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties,
        IncrementalInputProperties incrementalInputProperties
    ) {
        return new ExecutionStateChanges() {
            @Override
            public ImmutableList<String> getAllChangeMessages() {
                return allChangeMessages;
            }

            @Override
            public InputChangesInternal createInputChanges() {
                return new NonIncrementalInputChanges(inputFileProperties, incrementalInputProperties);
            }
        };
    }

    static ExecutionStateChanges rebuild(
        String rebuildReason,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties,
        IncrementalInputProperties incrementalInputProperties
    ) {
        return nonIncremental(ImmutableList.of(rebuildReason), inputFileProperties, incrementalInputProperties);
    }
}
