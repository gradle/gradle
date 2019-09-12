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

import org.gradle.tooling.events.internal.DefaultOperationSuccessResult;
import org.gradle.tooling.events.test.TestSuccessResult2;

/**
 * Implementation of the {@code TestSuccessResult} interface.
 */
public final class DefaultTestSuccessResult2 extends DefaultOperationSuccessResult implements TestSuccessResult2 {

    private final String output;
    private final String error;

    public DefaultTestSuccessResult2(long startTime, long endTime, String output, String error) {
        super(startTime, endTime);
        this.output = output;
        this.error = error;
    }

    public String getOutput() {
        return output;
    }

    @Override
    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "Tooling test success with output " +output;
    }
}
