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

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.concurrent.Stoppable;

import java.io.IOException;

@ThreadSafe
public interface FileWatcher extends Stoppable {

    /**
     * Is the watcher watching files.
     * <p>
     * This may return true while the watcher is in the process of stopping.
     * No events will be emitted by the watcher when this is false.
     * <p>
     * As the watcher can be stopped asynchronously,
     * the watcher may be stopped after it has emitted an event but before the event is received.
     *
     * @return is the watcher running.
     */
    boolean isRunning();

    void watch(FileSystemSubset fileSystemSubset) throws IOException;
}
