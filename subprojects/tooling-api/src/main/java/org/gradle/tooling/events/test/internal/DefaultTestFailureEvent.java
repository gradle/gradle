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

package org.gradle.tooling.events.test.internal;

import org.gradle.tooling.events.FailureOutcome;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.BaseFailureEvent;
import org.gradle.tooling.events.test.TestFailureEvent;
import org.gradle.tooling.events.test.TestOperationDescriptor;

/**
 * Implementation of the {@code TestFailureEvent} interface.
 */
public final class DefaultTestFailureEvent extends BaseFailureEvent implements TestFailureEvent {

    public DefaultTestFailureEvent(long eventTime, String displayName, OperationDescriptor descriptor, FailureOutcome outcome) {
        super(eventTime, displayName, descriptor, outcome);
    }

    @Override
    public TestOperationDescriptor getDescriptor() {
        return (TestOperationDescriptor) super.getDescriptor();
    }

}
