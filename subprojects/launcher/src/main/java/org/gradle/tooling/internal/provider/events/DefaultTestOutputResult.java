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

package org.gradle.tooling.internal.provider.events;

import org.gradle.tooling.internal.protocol.events.InternalOperationResult;
import org.gradle.tooling.internal.protocol.events.InternalTestOutputResult;

public class DefaultTestOutputResult extends AbstractResult implements InternalOperationResult, InternalTestOutputResult {

    private final int destination;
    private final String message;

    public DefaultTestOutputResult(long startTime, long endTime, int destination, String message) {
        super(startTime, endTime, "succeeded");
        this.destination = destination;
        this.message = message;
    }

    @Override
    public int getDestination() {
        return destination;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
