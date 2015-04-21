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

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.SkippedEvent;
import org.gradle.tooling.events.SuccessOutcome;
import org.gradle.tooling.events.SuccessResult;

/**
 * Default implementation of the {@code SkippedEvent} interface.
 */
public abstract class BaseSkippedEvent extends BaseProgressEvent implements SkippedEvent {

    private final SuccessResult result;

    protected BaseSkippedEvent(long eventTime, String eventDescription, OperationDescriptor descriptor, final SuccessOutcome outcome) {
        super(eventTime, eventDescription, descriptor);
        result = new SuccessResult() {
            @Override
            public SuccessOutcome getOutcome() {
                return outcome;
            }
        };

    }

    @Override
    public SuccessResult getResult() {
        return result;
    }

}
