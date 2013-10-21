/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.api.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe manager of the active mutexes in a (parallel) build.
 */
public class MutexManager {
    private final List<String> activeMutexes = new ArrayList<String>();

    /**
     * Atomically claim a mutex for the given task.
     * @param task the task to claim the mutex for.
     * @return <code>true</code> when no mutex to claim or when it was claimed successfully, otherwise <code>false</code>
     */
    public synchronized boolean claim(Task task) {
        String mutex = task.getMutex();
        if (mutex == null) {
            return true;
        }

        if (activeMutexes.contains(mutex)) {
            return false;
        }

        activeMutexes.add(mutex);
        return true;
    }

    /**
     * Atomically release a mutex claim.
     * @param task the task to release the mutex claim for.
     */
    public synchronized void release(Task task) {
        String mutex = task.getMutex();
        if (mutex == null) {
            return;
        }

        activeMutexes.remove(mutex);
    }
}
