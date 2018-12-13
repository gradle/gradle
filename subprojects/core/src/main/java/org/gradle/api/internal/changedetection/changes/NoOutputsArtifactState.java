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

package org.gradle.api.internal.changedetection.changes;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

import javax.annotation.Nullable;
import java.util.Optional;

class NoOutputsArtifactState implements TaskArtifactState {

    public static final TaskArtifactState WITHOUT_ACTIONS = new NoOutputsArtifactState("Task has not declared any outputs nor actions.");
    public static final TaskArtifactState WITH_ACTIONS = new NoOutputsArtifactState("Task has not declared any outputs despite executing actions.");
    private final String rebuildMessage;

    private NoOutputsArtifactState(String rebuildMessage) {
        this.rebuildMessage = rebuildMessage;
    }

    @Override
    public Optional<String> getRebuildReason() {
        return Optional.of(rebuildMessage);
    }

    @Override
    public boolean isAllowedToUseCachedResults() {
        return false;
    }

    @Override
    public Optional<BeforeExecutionState> getBeforeExecutionState(@Nullable AfterPreviousExecutionState afterPreviousExecutionState, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesBeforeExecution) {
        return Optional.empty();
    }
}
