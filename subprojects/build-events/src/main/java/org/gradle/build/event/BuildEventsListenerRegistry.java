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

package org.gradle.build.event;

import org.gradle.api.provider.Provider;
import org.gradle.tooling.events.OperationCompletionListener;

/**
 * Allows a plugin to receive information about the operations that run within a build.
 *
 * <p>An instance of this registry can be injected into tasks, plugins and other objects by annotating a public constructor or property getter method with {@code javax.inject.Inject}.</p>
 *
 * @since 6.1
 */
public interface BuildEventsListenerRegistry {
    /**
     * Subscribes the given listener to the finish events for tasks, if not already subscribed. The listener receives a {@link org.gradle.tooling.events.task.TaskFinishEvent} as each task completes.
     *
     * <p>The events are delivered to the listener one at a time, so the implementation does not need to be thread-safe. Also, events are delivered to the listener concurrently with
     * task execution and other work, so event handling does not block task execution. This means that a task finish event is delivered to the listener some time "soon" after the task
     * has completed. The events contain timestamps to allow you collect timing information.
     * </p>
     *
     * <p>The listener is automatically unsubscribed when the build finishes.</p>
     *
     * @param listener The listener to receive events. This must be a {@link org.gradle.api.services.BuildService} instance, see {@link org.gradle.api.services.BuildServiceRegistry}.
     */
    void onTaskCompletion(Provider<? extends OperationCompletionListener> listener);
}
