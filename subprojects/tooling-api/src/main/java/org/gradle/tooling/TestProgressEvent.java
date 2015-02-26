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
package org.gradle.tooling;

import org.gradle.api.Incubating;

/**
 * Some information about the progress of executing tests as part of running a build.
 *
 * @since 2.4
 */
@Incubating
public interface TestProgressEvent {

    /**
     * The id of the event type. The id can be resolved through {@link TestProgressEvent.EventType#valueOf(int)}.
     *
     * @return the id of the event type
     * @see EventType
     */
    int getEventTypeId();

    /**
     * The description of the test for which progress is reported.
     *
     * @return the test description
     */
    TestDescriptor getDescriptor();

    /**
     * The result of running the test, in case the test has finished.
     *
     * @return the test result, null if the test is still in progress
     */
    TestResult getResult();

    /**
     * Enumerates the different types of test events.
     */
    enum EventType {

        SUITE_STARTED(10), SUITE_SUCCEEDED(20), SUITE_FAILED(30), SUITE_SKIPPED(40),
        TEST_STARTED(50), TEST_SUCCEEDED(60), TEST_FAILED(70), TEST_SKIPPED(80);

        private int id;

        EventType(int id) {
            this.id = id;
        }

        public static EventType valueOf(int id) {
            for (EventType eventType : values()) {
                if (eventType.id == id) {
                    return eventType;
                }
            }
            throw new IllegalArgumentException(String.format("No EventType with id %d.", id));
        }

    }

}
