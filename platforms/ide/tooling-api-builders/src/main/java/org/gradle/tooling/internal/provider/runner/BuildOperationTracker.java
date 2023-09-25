/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationStartEvent;

import java.util.Collections;
import java.util.List;

/**
 * Tracks some state for build operations of a given type.
 */
public interface BuildOperationTracker {
    /**
     * Returns the trackers that are used by this tracker. If this tracker is required, then its trackers should be notified of
     * build operation execution. If this tracker is not required, the trackers can be ignored.
     */
    default List<? extends BuildOperationTracker> getTrackers() {
        return Collections.emptyList();
    }

    void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent);

    void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent);

    /**
     * Signals that the state for the given build operation is no longer required.
     */
    default void discardState(BuildOperationDescriptor buildOperation) {
    }
}
