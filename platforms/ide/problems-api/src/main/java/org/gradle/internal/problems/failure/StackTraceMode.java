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
package org.gradle.internal.problems.failure;

import org.jspecify.annotations.NullMarked;

/**
 * Whether a converted failure carries the stack frames of the original exception.
 * <p>
 * Stack frames dominate both the cost of converting a failure and the size of its serialized description.
 * {@link #OMIT} leaves the failure headers and structure intact without reading, copying, or classifying frames.
 *
 * @see FailureFactory
 */
@NullMarked
public enum StackTraceMode {

    /**
     * Convert each failure with all of its stack frames.
     */
    INCLUDE,

    /**
     * Convert failures and their suppressed exceptions without stack frames.
     */
    OMIT
}
