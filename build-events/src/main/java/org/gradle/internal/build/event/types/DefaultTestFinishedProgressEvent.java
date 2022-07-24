/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestFinishedProgressEvent;

public class DefaultTestFinishedProgressEvent extends AbstractProgressEvent<DefaultTestDescriptor> implements InternalTestFinishedProgressEvent, InternalOperationFinishedProgressEvent {
    private final AbstractTestResult result;

    public DefaultTestFinishedProgressEvent(long eventTime, DefaultTestDescriptor descriptor, AbstractTestResult result) {
        super(eventTime, descriptor);
        this.result = result;
    }

    @Override
    public AbstractTestResult getResult() {
        return result;
    }

    @Override
    public String getDisplayName() {
        return getDescriptor().getDisplayName() + " " + result.getOutcomeDescription();
    }
}
