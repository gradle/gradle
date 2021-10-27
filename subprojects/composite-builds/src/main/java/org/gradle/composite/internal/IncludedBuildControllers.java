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
package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.internal.build.ExecutionResult;

import java.io.Closeable;

interface IncludedBuildControllers extends Closeable {
    /**
     * Finish populating task graphs, once all entry point tasks have been scheduled.
     */
    void populateTaskGraphs();

    /**
     * Starts running any scheduled tasks. Does nothing when {@link #populateTaskGraphs()} has not been called to schedule the tasks.
     */
    void startTaskExecution();

    /**
     * Blocks until all scheduled tasks have completed.
     */
    ExecutionResult<Void> awaitTaskCompletion();

    IncludedBuildController getBuildController(BuildIdentifier buildIdentifier);

    @Override
    void close();
}
