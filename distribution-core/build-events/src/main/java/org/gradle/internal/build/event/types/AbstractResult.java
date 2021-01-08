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

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public abstract class AbstractResult implements Serializable {
    private final long startTime;
    private final long endTime;
    private final String outcomeDescription;

    public AbstractResult(long startTime, long endTime, String outcomeDescription) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.outcomeDescription = outcomeDescription;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getOutcomeDescription() {
        return outcomeDescription;
    }

    public List<DefaultFailure> getFailures() {
        return Collections.emptyList();
    }
}
