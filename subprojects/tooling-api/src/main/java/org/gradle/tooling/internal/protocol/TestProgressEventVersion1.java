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

    String OUTCOME_STARTED = "STARTED";
    String OUTCOME_SKIPPED = "SKIPPED";
    String OUTCOME_SUCCEEDED = "SUCCEEDED";
    String OUTCOME_FAILED = "FAILED";

    /**
     * Returns the time when the event happened.
     *
     * @return The event time
     */
    long getEventTime();

    /**
     * Returns the outcome of the test that has progressed. See the constants on this interface for the supported outcome types.
     *
     * @return The outcome of the progressed test (started, skipped, etc.)
     */
    String getTestOutcome();

    /**
     * Returns the description of the test for which progress is reported.
     *
     * @return The test description
     */
    TestDescriptorVersion1 getDescriptor();

    /**
     * Returns the result of running the test successfully or with a failure.
     *
     * @return The test result
     */
    TestResultVersion1 getResult();

}
