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

package org.gradle.tooling.events.internal.build.internal;

import org.gradle.tooling.events.internal.build.BuildOperationProgressEvent;

/**
 * A listener which is notified when the operations that are executed as part of running a build make progress.
 */
public interface BuildOperationProgressListener {

    /**
     * Called when the build execution progresses.
     *
     * The following events are currently issued:
     * <ul>
     *     <li>{@link org.gradle.tooling.events.internal.build.BuildOperationStartEvent}</li>
     *     <li>{@link org.gradle.tooling.events.internal.build.BuildOperationFinishEvent}</li>
     * </ul>
     *
     * @param event An event describing the build operation progress.
     */
    void statusChanged(BuildOperationProgressEvent event);

}
