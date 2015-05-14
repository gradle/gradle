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
 * Enumerates the different types of progress events that can be received.
 *
 * @see org.gradle.tooling.LongRunningOperation#addProgressListener(ProgressListener, java.util.EnumSet)
 */
public enum ProgressEventType {

    /**
     * Flag for test operation progress events.
     */
    TEST,

    /**
     * Flag for task operation progress events.
     */
    TASK,

    /**
     * Flag for progress events for which there is no specific operation type.
     */
    GENERIC

}
