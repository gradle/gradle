/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.cache.internal;

import java.io.Closeable;
import java.io.File;

/**
 * A coordinator that can be used to manage access to decompressed data extracted from archive files like zip and tars.
 *
 * <p>
 * For a given build tree, only a single process is allowed write access to extract archives at a time.
 *
 * Multiple build processes are allowed to extract archives concurrently as long as they use different build trees (in other words, different root directories).
 * <p>
 * Within a process, only one thread is allowed write access to a given expanded directory. This is to avoid concurrent writes
 * to the same expanded directory.
 *
 * Multiple threads are allowed to extract into different expanded directories concurrently.
 * <p>
 * There currently are no checks on modifications to files in the expanded directory. This can cause problems if the expanded directory is used
 * by multiple tasks and each task expects different modifications to be made to the extracted files.
 */
public interface DecompressionCoordinator extends Closeable {

    /**
     * Runs the given action while holding the lock for the given key.
     * <p>
     * The key is based on the given expanded directory used to extract the archive file.
     *
     * @param expandedDir The directory in use for the action
     * @param action The action to run while the cache is held for the given key
     */
    void exclusiveAccessTo(File expandedDir, Runnable action);
}
