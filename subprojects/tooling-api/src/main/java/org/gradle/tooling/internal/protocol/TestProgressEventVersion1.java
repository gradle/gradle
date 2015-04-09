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
package org.gradle.tooling.internal.protocol;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 */
public interface TestProgressEventVersion1 {

    String TEST_SUITE_STARTED = "TEST_SUITE_STARTED";
    String TEST_SUITE_SKIPPED = "TEST_SUITE_SKIPPED";
    String TEST_SUITE_SUCCEEDED = "TEST_SUITE_SUCCEEDED";
    String TEST_SUITE_FAILED = "TEST_SUITE_FAILED";

    String TEST_STARTED = "TEST_STARTED";
    String TEST_SKIPPED = "TEST_SKIPPED";
    String TEST_SUCCEEDED = "TEST_SUCCEEDED";
    String TEST_FAILED = "TEST_FAILED";

    /**
     * The specific type of test progress. See the constants on this interface for the supported types.
     *
     * @return The type of test progress (started, skipped, etc.)
     */
    String getEventType();

    /**
     * The time when the event happened.
     *
     * @return The event time
     */
    long getEventTime();

    /**
     * The description of the test for which progress is reported.
     *
     * @return The test description
     */
    TestDescriptorVersion1 getDescriptor();

    /**
     * The result of running the test successfully or with a failure.
     *
     * @return The test result
     */
    TestResultVersion1 getResult();

}
