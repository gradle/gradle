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

package org.gradle.internal.filewatch;

import org.gradle.internal.concurrent.Stoppable;

import java.io.IOException;

/**
 * Stateful service for creating for multiple watches on different sets of inputs of {@link org.gradle.api.file.DirectoryTree} or individual {@link java.io.File}s
 *
 * This is designed to be used in a loop so that all watches get registered again every time. The boundaries of the "registration mode" are marked by calling
 * enterRegistrationMode and exitRegistrationMode.
 *
 * All watching can be stopped by calling the stop method.
 *
 */
public interface FileWatcher extends Stoppable {
    /**
     * Starts watching for file changes to given inputs.
     * It is guaranteed that file watching gets activated before this method returns.
     *
     * The sourceKey parameter is used for continuous watching logic to skip any change events that are happening after
     * "enterRegistrationMode" has been called, but before watch has been called to re-activate the watch. If the inputs haven't changed,
     * the watch will be kept active.
     *
     * @param sourceKey a unique external key for this watch. For gradle tasks, the unique task path can be used as a key.
     * @param inputs the directories and files to watch for changes
     */
    void watch(String sourceKey, FileWatchInputs inputs) throws IOException;

    /**
     * this method should be called before adding any watches
     *
     * it is used to mark the boundaries of the watches so that any stale watches from the previous loop can be removed when exiting the registration mode
     */
    void enterRegistrationMode();

    /**
     * this method is for marking the exit boundary of adding watches.
     *
     * any stale watches from the previous loop will be removed at this point. watches can be reused in the next "round" of registrations
     */
    void exitRegistrationMode();
}
