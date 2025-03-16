/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;

/**
 * Standard output or standard error message logged during the execution of the test
 */
public interface TestOutputEvent {

    /**
     * The time the message was logged, in milliseconds since UNIX epoch.
     *
     * @since 8.12
     */
    @Incubating
    long getLogTime();

    /**
     * Destination of the message
     */
    Destination getDestination();

    /**
     * Message content
     */
    String getMessage();

    /**
     * Destination of the message
     */
    enum Destination {
        StdOut, StdErr
    }
}
