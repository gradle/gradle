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

import org.gradle.tooling.internal.protocol.TestResultVersion1;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public abstract class InternalTestResult implements Serializable, TestResultVersion1 {
    private final long startTime;
    private final long endTime;

    public InternalTestResult(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public abstract String getOutcomeDescription();

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<InternalFailure> getFailures() {
        return Collections.emptyList();
    }

}
