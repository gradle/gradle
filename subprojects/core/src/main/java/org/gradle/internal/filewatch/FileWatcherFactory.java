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

import org.gradle.api.Action;

public interface FileWatcherFactory {

    /**
     * Starts watching for changes to the given subset of the file system.
     * <p>
     * Listeners should not rely on the events being an entirely accurate journal of file system events.
     * Implementations may emit duplicate events and chronological ordering is not guaranteed.
     * <p>
     * It can be assumed that all changes to the file system subset that occur <b>after</b> the return of this method will produce events.
     * <p>
     * No events will be emitted after that watcher has been stopped.
     *
     * @param onError what to do if an error occurs while watching
     * @param listener the receiver of events
     * @return a, stoppable, handle to the watcher
     */
    FileWatcher watch(Action<? super Throwable> onError, FileWatcherListener listener);
}
