/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.internal.operations.BuildOperationWorkerRegistry.Operation;
import org.gradle.internal.progress.BuildOperationExecutor;

/**
 * A service that executes work in a (potentially) long-lived process.
 */
public interface WorkerDaemon {
    <T extends WorkSpec> DefaultWorkResult execute(WorkerDaemonAction<T> action, T spec);
    <T extends WorkSpec> DefaultWorkResult execute(WorkerDaemonAction<T> action, T spec, Operation parentWorkerOperation, BuildOperationExecutor.Operation parentBuildOperation);
}
