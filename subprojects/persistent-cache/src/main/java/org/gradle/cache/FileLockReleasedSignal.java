/*
 * Copyright 2018 the original author or authors.
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

/**
 * Signal that a file lock has been released.
 *
 * @see org.gradle.cache.internal.locklistener.FileLockContentionHandler
 */
public interface FileLockReleasedSignal {

    /**
     * Triggers this signal to notify the lock requesters that the file
     * lock has been released.
     *
     * <p>Returns once the signal has been emitted but not necessarily
     * received by the lock requesters.
     */
    void trigger();

}
