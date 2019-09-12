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

package org.gradle.tooling.events.test.internal;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.internal.DefaultOperationFailureResult;
import org.gradle.tooling.events.test.TestFailureResult;
import org.gradle.tooling.events.test.TestFailureResult2;

import java.util.List;

/**
 * Implementation of the {@code TestFailureResult} interface.
 */
public final class DefaultTestFailureResult2 extends DefaultOperationFailureResult implements TestFailureResult2 {

    private final String output;
    private final String error;

    public DefaultTestFailureResult2(long startTime, long endTime, List<? extends Failure> failures, String output, String error) {
        super(startTime, endTime, failures);
        this.output = output;
        this.error = error;
    }

    @Override
    public String getOutput() {
        return output;
    }

    @Override
    public String getError() {
        return error;
    }
}
