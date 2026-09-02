/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.internal.build.event.types;

import org.jspecify.annotations.NullMarked;

/**
 * Whether a converted failure's description carries the stack frames of the original exception.
 * <p>
 * A failure carries its stack trace as text inside its description, so the frames dominate both the cost of converting
 * it and the size of what is sent to the client. {@link #OMIT} drops them, leaving the headers and the failure
 * structure.
 *
 * @see DefaultFailure
 */
@NullMarked
public enum StackTraceMode {

    /**
     * Describe each failure the way {@link Throwable#printStackTrace()} would, frames included.
     */
    INCLUDE,

    /**
     * Describe failures and their suppressed exceptions without rendering stack frames, so neither a frame line nor the
     * "... n more" line that elides a common frame tail appears anywhere in the failure tree.
     */
    OMIT
}
