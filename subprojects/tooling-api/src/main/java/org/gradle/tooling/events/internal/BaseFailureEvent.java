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

package org.gradle.tooling.events.internal;

import org.gradle.tooling.events.FailureEvent;
import org.gradle.tooling.events.FailureOutcome;
import org.gradle.tooling.events.FailureResult;
import org.gradle.tooling.events.OperationDescriptor;

/**
 * Default implementation of the {@code FailureEvent} interface.
 */
public abstract class BaseFailureEvent extends BaseProgressEvent implements FailureEvent {

    private final FailureResult result;

    protected BaseFailureEvent(long eventTime, String displayName, OperationDescriptor descriptor, final FailureOutcome outcome) {
        super(eventTime, displayName, descriptor);
        result = new FailureResult() {
            @Override
            public FailureOutcome getOutcome() {
                return outcome;
            }
        };
    }

    @Override
    public FailureResult getResult() {
        return result;
    }

}
