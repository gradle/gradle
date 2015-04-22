/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.tooling.internal.protocol.TestFailureResultVersion1;

import java.util.List;

public class InternalTestFailureResult extends InternalTestResult implements TestFailureResultVersion1 {
    private final List<InternalFailure> failures;

    public InternalTestFailureResult(long startTime, long endTime, List<InternalFailure> failures) {
        super(startTime, endTime);
        this.failures = failures;
    }

    @Override
    public String getOutcomeDescription() {
        return "failed";
    }

    @Override
    public List<InternalFailure> getFailures() {
        return failures;
    }
}
