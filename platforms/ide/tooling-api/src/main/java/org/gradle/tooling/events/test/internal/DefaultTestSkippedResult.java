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

package org.gradle.tooling.events.test.internal;

import org.gradle.tooling.events.test.TestSkippedResult;

/**
 * Implementation of the {@code TestSkippedResult} interface.
 */
public final class DefaultTestSkippedResult implements TestSkippedResult {

    private final long startTime;
    private final long endTime;

    public DefaultTestSkippedResult(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

}
