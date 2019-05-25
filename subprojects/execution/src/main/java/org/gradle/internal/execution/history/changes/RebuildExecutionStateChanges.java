/*
 * Copyright 2019 the original author or authors.
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

import javax.annotation.Nullable;

public class RebuildExecutionStateChanges implements ExecutionStateChanges {
    private final String rebuildReason;
    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties;
    private final IncrementalInputProperties incrementalInputProperties;

    public RebuildExecutionStateChanges(
        String rebuildReason,
        @Nullable ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties,
        IncrementalInputProperties incrementalInputProperties
    ) {
        this.rebuildReason = rebuildReason;
        this.inputFileProperties = inputFileProperties;
        this.incrementalInputProperties = incrementalInputProperties;
    }

    @Override
    public ImmutableList<String> getAllChangeMessages() {
        return ImmutableList.of(rebuildReason);
    }

    @Override
    public InputChangesInternal createInputChanges() {
        if (inputFileProperties == null) {
            throw new UnsupportedOperationException("Cannot query input changes when input tracking is disabled.");
        }
        return new NonIncrementalInputChanges(inputFileProperties, incrementalInputProperties);
    }

    @Override
    public ExecutionStateChanges withEnforcedRebuild(String rebuildReason) {
        return new RebuildExecutionStateChanges(rebuildReason, inputFileProperties, incrementalInputProperties);
    }
}
