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

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.FailureResult;

import java.util.List;

/**
 * Implementation of the {@code BuildFailureResult} interface.
 */
public class DefaultOperationFailureResult implements FailureResult {

    private final long startTime;
    private final long endTime;
    private final List<? extends Failure> failures;

    public DefaultOperationFailureResult(long startTime, long endTime, List<? extends Failure> failures) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.failures = failures;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    @Override
    public List<? extends Failure> getFailures() {
        return failures;
    }

}
