/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.events;

/**
 * Enumerates the different types of operations for which progress events can be received.
 *
 * @see org.gradle.tooling.LongRunningOperation#addProgressListener(ProgressListener, java.util.Set)
 */
public enum OperationType {

    /**
     * Flag for test operation progress events.
     */
    TEST,

    /**
     * Flag for task operation progress events.
     */
    TASK,

    /**
     * Flag for operations with no specific type.
     */
    GENERIC,

    /**
     * Flag for work item operation progress events.
     *
     * @since 5.1
     */
    WORK_ITEM,

    /**
     * Flag for project configuration operation progress events.
     *
     * @since 5.1
     */
    PROJECT_CONFIGURATION,

    /**
     * Flag for transform operation progress events.
     *
     * @since 5.1
     */
    TRANSFORM,

    /**
     *  Flag for test output operation progress events.
     */
    TEST_OUTPUT

}
