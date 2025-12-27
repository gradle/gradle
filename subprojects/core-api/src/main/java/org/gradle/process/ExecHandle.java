/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process;

import org.gradle.api.Describable;
import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;

/**
 * Represents the result of running an external process.
 *
 * The process will be started immediately.
 *
 * @see org.gradle.process.ExecOperations#execAsync
 * @since 9.4.0
 */
@Incubating
@NullMarked
public interface ExecHandle extends Describable {

    /**
     * Blocks until the process has exited (either successfully or unsuccessfully).
     *
     * Does nothing if the process has already completed.
     *
     * @since 9.4.0
     */
    ExecResult waitForFinish();

    /**
     * Aborts the process, blocking until the process has exited. Does nothing if the process has already completed.
     *
     * @since 9.4.0
     */
    void abort();
}
