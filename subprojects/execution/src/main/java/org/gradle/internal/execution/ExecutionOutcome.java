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

package org.gradle.internal.execution;

public enum ExecutionOutcome {
    /**
     * The outputs haven't been changed, because the work is already up-to-date
     * (i.e. its inputs and outputs match that of the previous execution in the
     * same workspace).
     */
    UP_TO_DATE,

    /**
     * The outputs of the work have been loaded from the build cache.
     */
    FROM_CACHE,

    /**
     * Executing the work was not necessary to produce the outputs.
     * This is usually due to the work having no inputs to process.
     */
    SHORT_CIRCUITED,

    /**
     * The work has been executed with information about the changes that happened since the previous execution.
     */
    EXECUTED_INCREMENTALLY,

    /**
     * The work has been executed with no incremental change information.
     */
    EXECUTED_NON_INCREMENTALLY
}
