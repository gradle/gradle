/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.cache;

import org.gradle.cache.internal.FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanup;
import org.jspecify.annotations.NullMarked;

import java.io.Closeable;
import java.util.function.Supplier;

/**
 * A persistent cache that locks fine-grained: it has one lock per key instead of one lock for the whole cache.
 *
 * <p>
 * Cache will always use {@link FileLockManager.LockMode#OnDemandEagerRelease} lock mode for key locks.
 * Cache doesn't support cross-version mode.
 * </p>
 *
 * <p>
 * Key format: the cache key is a logical identifier, not a filesystem path. Keys must not contain
 * any path separators (e.g. '/' or '\\'), and must not start with a dot character ('.').
 * Implementations are expected to reject such keys.
 * </p>
 *
 * <p>
 * IMPORTANT: If an implementation uses file locking then it must place ALL per-key lock files under a dedicated directory located at
 * {@code <base-dir>/.internal/locks} (see {@link #LOCKS_DIR_RELATIVE_PATH}). Each lock file must be named using the cache key plus the
 * {@code ".lock"} suffix, i.e. {@code "<base-dir>/.internal/locks/<key>.lock"}. Cleanup strategies can then rely on this convention to locate and safely
 * remove orphaned lock files without requiring additional API calls.
 * Directory {@code ".internal"} can be used to store any internal metadata required by the cache implementation.
 * </p>
 *
 * <p>
 * Example disk layout of this cache:
 * <pre>
 * │{@code <base-dir>/}
 * ├── {@code <key-1>/}         # entry content created by the caller (directory or file)
 * ├── {@code <key-2>}          # another entry (keys are logical, can map to a file or dir, but without extensions)
 * └── .internal/               # reserved for cache metadata
 *     ├── locks/               # per-key lock files
 *     │   ├── {@code <key-1>.lock}
 *     │   └── {@code <key-2>.lock}
 *     ├── gc.properties        # Example cleanup heartbeat/metadata file for the cache
 *     └── gc/                  # per-key cleanup metadata as created by {@link FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanup}
 *         ├── {@code <key-1>/}
 *         │   ├── soft.deleted
 *         │   └── gc.properties
 *         └── {@code <key-2>/}
 *             ├── soft.deleted
 *             └── gc.properties
 * </pre>
 */
@NullMarked
public interface FineGrainedPersistentCache extends Closeable, CleanableStore, HasCleanupAction {

    /**
     * A path to internal directory, relative to {@link #getBaseDir()}, where all metadata should be stored.
     */
    String INTERNAL_DIR_PATH = ".internal";

    /**
     * A path to locks directory, relative to {@link #getBaseDir()}, where all key lock files must be stored.
     */
    String LOCKS_DIR_RELATIVE_PATH = ".internal/locks";

    /**
     * Opens this cache and returns self.
     */
    FineGrainedPersistentCache open();

    /**
     * Performs some work against the cache for {@code <base-dir>/<key>} path.
     *
     * Acquires exclusive locks on the {@code <base-dir>/<key>} path, so that the given action is the only action to execute across all threads and processes.
     * Releases the locks and all resources at the end of the action.
     */
    <T> T useCache(String key, Supplier<? extends T> action);

    /**
     * The same as {@link #useCache(String, Supplier)} for actions that don't return any result.
     */
    void useCache(String key, Runnable action);

    /**
     * Performs some work against the cache for {@code <base-dir>/<key>} path in the same way as {@link #useCache(String, Supplier)} but without a thread lock.
     *
     * This should be used only if thread locking is handled by the caller already. Otherwise prefer {@link #useCache(String, Supplier)}.
     */
    <T> T withFileLock(String key, Supplier<? extends T> action);

    /**
     * The same as {@link #withFileLock(String, Supplier)} for actions that don't return any result.
     */
    void withFileLock(String key, Runnable action);

    /**
     * Closes this cache, blocking until all operations are complete.
     */
    @Override
    void close();
}
