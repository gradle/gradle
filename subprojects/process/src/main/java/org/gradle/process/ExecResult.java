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

import org.gradle.process.internal.ExecException;

/**
 * Represents the result of running an external process.
 */
public interface ExecResult {
    /**
     * Returns the exit value of the process.
     */
    int getExitValue();

    /**
     * Throws an {@link org.gradle.process.internal.ExecException} if the process exited with a non-zero exit value.
     *
     * @return this
     * @throws ExecException if the process exited with a non-zero exit value
     */
    ExecResult assertNormalExitValue() throws ExecException;

    /**
     * Re-throws any failure executing this process.
     *
     * @return this
     * @throws ExecException the execution failure
     */
    ExecResult rethrowFailure() throws ExecException;
}
