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

import org.gradle.tooling.events.AdvanceEvent;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.OperationResult;

/**
 * Base implementation of the {@code AdvanceEvent} interface.
 */
public abstract class BaseAdvanceEvent extends BaseProgressEvent implements AdvanceEvent {

    private final OperationResult result;

    protected BaseAdvanceEvent(long eventTime, String displayName, OperationDescriptor descriptor, OperationResult intermediateResult) {
        super(eventTime, displayName, descriptor);
        this.result = intermediateResult;
    }

    /**
     * Returns the result of the current operation.
     *
     * @return the result of the current operation
     */
    @Override
    public OperationResult getResult() {
        return result;
    }
}
