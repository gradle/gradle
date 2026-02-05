/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.workspace;

import java.io.File;
import java.util.function.Supplier;

public interface ImmutableWorkspaceProvider {
    ImmutableWorkspace getWorkspace(String workId);

    interface ImmutableWorkspace {
        /**
         * Immutable workspace outputs location.
         *
         * This will normally be $GRADLE_USER_HOME/caches/[gradle-version]/[cache-name]/[hash]/
         */
        File getImmutableLocation();

        /**
         * Executes the given action with process file lock.
         */
        <T> T withFileLock(Supplier<T> action);

        /**
         * Gets a result from the workspace if it's already running or computes it otherwise.
         *
         * This method makes sure only one thread is executing the given action for a workspace at a time.
         */
        <T> ConcurrentResult<T> getOrCompute(Supplier<T> action);

        /**
         * Returns true if the workspace has been soft deleted.
         */
        boolean isSoftDeleted();

        /**
         * Remove a soft deletion marker, which means entry won't be deleted anymore.
         */
        void ensureUnSoftDeleted();
    }

    class ConcurrentResult<T> {

        private final T value;
        private final boolean isProducedByCurrentThread;

        private ConcurrentResult(T value, boolean isProducedByCurrentThread) {
            this.value = value;
            this.isProducedByCurrentThread = isProducedByCurrentThread;
        }

        public T get() {
            return value;
        }
        public boolean isProducedByCurrentThread() {
            return isProducedByCurrentThread;
        }

        public static <T> ConcurrentResult<T> producedByCurrentThread(T value) {
            return new ConcurrentResult<>(value, true);
        }

        public static <T> ConcurrentResult<T> producedByOtherThread(T value) {
            return new ConcurrentResult<>(value, false);
        }
    }
}
