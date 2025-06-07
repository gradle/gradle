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

package org.gradle.process;

import org.jspecify.annotations.NullMarked;

/**
 * An exception thrown when an error occurs while executing a process.
 *
 * @since 9.0.0
 */
@NullMarked
@SuppressWarnings("deprecation")
public class ProcessExecutionException extends org.gradle.process.internal.ExecException {

    /**
     * Creates a new instance of {@code ExecException} with the specified message.
     *
     * @since 9.0.0
     */
    public ProcessExecutionException(String message) {
        super(message);
    }

    /**
     * Creates a new instance of {@code ExecException} with the specified message and cause.
     *
     * @since 9.0.0
     */
    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
