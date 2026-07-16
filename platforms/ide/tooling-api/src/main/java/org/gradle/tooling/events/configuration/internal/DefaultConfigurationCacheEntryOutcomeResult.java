/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.events.configuration.internal;

import org.gradle.tooling.events.configuration.ConfigurationCacheEntryOutcomeResult;
import org.gradle.tooling.events.internal.DefaultOperationSuccessResult;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class DefaultConfigurationCacheEntryOutcomeResult extends DefaultOperationSuccessResult implements ConfigurationCacheEntryOutcomeResult {
    private final String outcome;
    private final int problemCount;

    public DefaultConfigurationCacheEntryOutcomeResult(long startTime, long endTime, String outcome, int problemCount) {
        super(startTime, endTime);
        this.outcome = outcome;
        this.problemCount = problemCount;
    }

    @Override
    public String getOutcome() {
        return outcome;
    }

    @Override
    public int getProblemCount() {
        return problemCount;
    }
}
