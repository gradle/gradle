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

import org.gradle.api.Incubating;
import org.gradle.tooling.Descriptor;
import org.gradle.tooling.TestDescriptor;

/**
 * A test event is an event representing a test execution phase.
 *
 * @since 2.4
 */
@Incubating
public class TestEvent extends AbstractEvent implements TestProgressEvent {

    protected final TestKind testKind;
    private final String description;
    private final TestDescriptor testDescriptor;

    public TestEvent(long eventTime, String description, TestDescriptor testDescriptor, TestKind testKind) {
        super(eventTime);
        this.description = description;
        this.testDescriptor = testDescriptor;
        this.testKind = testKind;
    }

    /**
     * The description of the Test entity for which progress is reported.
     *
     * @return The description of the underlying Test entity.
     */
    @Override
    public Descriptor getDescriptor() {
        return getTestDescriptor();
    }

    /**
     * The test descriptor of this event
     *
     * @return a test descriptor
     */
    public TestDescriptor getTestDescriptor() {
        return testDescriptor;
    }

    /**
     * A description of the test
     *
     * @return description of the test
     */
    public String getDescription() {
        return description;
    }

    /**
     * The kind of the test (unit test, test suite, unknown, ...)
     *
     * @return the test kind
     */
    public TestKind getTestKind() {
        return testKind;
    }
}
