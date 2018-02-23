/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.events;

import org.gradle.api.Incubating;

/**
 * A listener which is notified when operations that are executed as part of running a build make progress.
 *
 * @see org.gradle.tooling.LongRunningOperation#addProgressListener(ProgressListener)
 * @see org.gradle.tooling.LongRunningOperation#addProgressListener(ProgressListener, java.util.Set)
 * @since 2.5
 */
@Incubating
public interface ProgressListener {

    /**
     * Called when the execution of an operation progresses.
     *
     * The following operation-specific events are currently issued:
     * <ul>
     *     <li>{@link org.gradle.tooling.events.test.TestStartEvent}</li>
     *     <li>{@link org.gradle.tooling.events.test.TestFinishEvent}</li>
     *     <li>{@link org.gradle.tooling.events.task.TaskStartEvent}</li>
     *     <li>{@link org.gradle.tooling.events.task.TaskFinishEvent}</li>
     * </ul>
     *
     * For all other operations, the following generic events are currently issued :
     * <ul>
     *     <li>{@link StartEvent}</li>
     *     <li>{@link FinishEvent}</li>
     * </ul>
     *
     * You can find out more about the operation for which progress is reported
     * by querying the descriptor using {@link org.gradle.tooling.events.ProgressEvent#getDescriptor()}.
     *
     * @param event An event describing the operation progress.
     * @see org.gradle.tooling.events.test.TestProgressEvent
     * @see org.gradle.tooling.events.task.TaskProgressEvent
     * @see org.gradle.tooling.events.ProgressEvent
     */
    void statusChanged(ProgressEvent event);

}
