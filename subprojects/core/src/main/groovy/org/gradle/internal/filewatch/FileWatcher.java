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

/**
 * Stateful service for watching changes on multiple {@link org.gradle.api.file.DirectoryTree} or individual {@link java.io.File}s
 *
 * Designed to be used with a single set of inputs and a single listener.
 *
 * Instance should be stopped by calling the stop method before letting go of the instance to free any resources used by the FileWatcher instance
 *
 */
public interface FileWatcher extends Stoppable {
    /**
     * Starts watching for file changes on a separate background thread.
     *
     * @param inputs the directories and files to watch for changes
     * @param listener the listener to report changes asynchronously
     */
    void watch(FileWatchInputs inputs, FileWatchListener listener);
}
