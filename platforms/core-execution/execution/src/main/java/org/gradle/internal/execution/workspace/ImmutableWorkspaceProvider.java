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
import java.util.function.Function;
import java.util.function.Supplier;

public interface ImmutableWorkspaceProvider {
    ImmutableWorkspace getWorkspace(String path);

    interface ImmutableWorkspace {
        /**
         * Immutable workspace outputs location.
         *
         * For {@link LockingImmutableWorkspace} this will normally be $GRADLE_USER_HOME/caches/transforms/[gradle-version]/[hash]/workspace/
         *     and for {@link AtomicMoveImmutableWorkspace} this will normally be $GRADLE_USER_HOME/caches/transforms/[gradle-version]/[hash]/
         */
        File getImmutableLocation();

        /**
         * Executes the given action with process file lock.
         */
        <T> T withFileLock(Supplier<T> action);

        /**
         * Gets a result from the workspace if it's already running or computes it otherwise.
         *
         * This method makes sure only one thread is executing the given action for Workspace at a time.
         *
         * <p>
         * If a result from another thread already exists then {@code concurrentResultMapper} is called, otherwise {@code action} is executed.
         * </p>
         */
        <T> T getIfRunningOrCompute(Function<T, T> concurrentResultMapper, Supplier<T> action);

        /**
         * Returns true if the workspace has been soft deleted.
         */
        boolean isSoftDeleted();

        /**
         * Remove soft deletion marker, which means entry won't be deleted anymore.
         */
        void ensureUnSoftDeleted();
    }

    /**
     * A workspace that relies on locking to ensure that only one process can access it at a time.
     * Used on Windows where atomic moves cause issues with file locking.
     */
    interface LockingImmutableWorkspace extends ImmutableWorkspace {

        /**
         * Executes the given action under the global scoped lock.
         */
        <T> T withWorkspaceLock(Supplier<T> supplier);
    }

    /**
     * A workspace that relies on atomic moves of immutable workspace directory.
     * Used on Unix-like systems where atomic moves are supported.
     */
    interface AtomicMoveImmutableWorkspace extends ImmutableWorkspace {

        /**
         * Provides a temporary workspace and executes the given action in it.
         */
        <T> T withTemporaryWorkspace(TemporaryWorkspaceAction<T> action);

        @FunctionalInterface
        interface TemporaryWorkspaceAction<T> {
            T executeInTemporaryWorkspace(File temporaryWorkspaceLocation);
        }
    }
}
