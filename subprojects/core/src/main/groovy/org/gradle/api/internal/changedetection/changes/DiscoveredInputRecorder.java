/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.changes;

import java.io.File;

public interface DiscoveredInputRecorder {
    /**
     * Registers files as "discovered" inputs to the task. All inputs added this way must be added each time the task is executed.
     * <p>
     * Discovered inputs should be derived from inputs registered with {@link org.gradle.api.tasks.TaskInputs}.
     * <p>
     * This method may be called at any time.
     * </p>
     * @param discoveredInput New input files discovered as part of the task's action.
     */
    void newInput(File discoveredInput);
}
