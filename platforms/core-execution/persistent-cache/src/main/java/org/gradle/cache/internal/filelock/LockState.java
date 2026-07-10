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

package org.gradle.cache.internal.filelock;

import org.gradle.cache.FileLock;

/**
 * An immutable snapshot of the state of a lock.
 */
public interface LockState extends FileLock.State {
    boolean isDirty();

    @Override
    boolean isInInitialState();

    /**
     * Called after an update is complete, returns a new clean state based on this state.
     */
    LockState completeUpdate();

    /**
     * Called before an update is complete, returns a new dirty state based on this state.
     */
    LockState beforeUpdate();
}
