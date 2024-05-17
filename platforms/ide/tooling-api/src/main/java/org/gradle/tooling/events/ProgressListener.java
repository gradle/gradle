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

/**
 * A listener which is notified when operations that are executed as part of running a build make progress.
 *
 * @see org.gradle.tooling.LongRunningOperation#addProgressListener(ProgressListener)
 * @see org.gradle.tooling.LongRunningOperation#addProgressListener(ProgressListener, java.util.Set)
 * @since 2.5
 */
public interface ProgressListener {

    /**
     * Called when the execution of an operation progresses.
     * <p>
     * The possible progress event types are listed in {@link OperationType}'s documentation.
     *</p>
     * <p>
     * You can find out more about the operation for which progress is reported
     * by querying the descriptor using {@link org.gradle.tooling.events.ProgressEvent#getDescriptor()}.
     * </p>
     *
     * @param event An event describing the operation progress.
     * @see org.gradle.tooling.events.ProgressEvent
     * @see OperationType
     */
    void statusChanged(ProgressEvent event);

}
