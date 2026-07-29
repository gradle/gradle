/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.internal.work.SubmissionQueue;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;

public interface BuildOperationQueueFactory {
    /**
     * @param constrainedQueue receives work added via {@link BuildOperationQueue#add}, which must hold a worker lease
     * @param unconstrainedExecutor runs work added via {@link BuildOperationQueue#addUnconstrained}, which must not
     */
    <T extends BuildOperation> BuildOperationQueue<T> create(
        SubmissionQueue constrainedQueue,
        Executor unconstrainedExecutor,
        boolean allowAccessToProjectState,
        BuildOperationQueue.QueueWorker<T> worker,
        @Nullable BuildOperationRef parent
    );
}
