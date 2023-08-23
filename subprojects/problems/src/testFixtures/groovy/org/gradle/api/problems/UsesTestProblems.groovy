/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems


import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.OperationIdentifier

/**
 * Utility to be implemented by test classes that provides methods for creating [Problems] instances for testing.
 */
interface UsesTestProblems {
    /**
     * A [BuildOperationProgressEventEmitter] that does nothing.
     */
    static class NoOpBuildOperationProgressEventEmitter implements BuildOperationProgressEventEmitter {
        @Override
        void emit(OperationIdentifier operationIdentifier, long timestamp, Object details) {}

        @Override
        void emitNowIfCurrent(Object details) {}

        @Override
        void emitIfCurrent(long time, Object details) {}

        @Override
        void emitNowForCurrent(Object details) {}
    }

    /**
     * Creates a new [Problems] instance that does not report problems as build operation events.
     *
     * @return the problems instance
     */
    default Problems createTestProblems() {
        return new DefaultProblems(new NoOpBuildOperationProgressEventEmitter())
    }
}
