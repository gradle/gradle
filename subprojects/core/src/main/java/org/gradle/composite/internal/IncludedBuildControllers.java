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
package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.function.Consumer;

@ServiceScope(Scopes.BuildTree.class)
public interface IncludedBuildControllers {
    /**
     * Notify the controllers that the root build operation has started.
     * Should be using something like {@link org.gradle.initialization.RootBuildLifecycleListener} however, this is currently called outside the root build operation.
     */
    void rootBuildOperationStarted();

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
    void awaitTaskCompletion(Consumer<? super Throwable> taskFailures);

    /**
     * Completes the build, blocking until complete.
     */
    void finishBuild(Consumer<? super Throwable> collector);

    IncludedBuildController getBuildController(BuildIdentifier buildIdentifier);
}
