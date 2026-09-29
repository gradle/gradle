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

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryStoreFailedResult;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public class DefaultConfigurationCacheEntryStoreFailedResult extends AbstractConfigurationCacheEntryOutcomeResult implements ConfigurationCacheEntryStoreFailedResult {
    private final List<? extends Failure> failures;

    public DefaultConfigurationCacheEntryStoreFailedResult(long startTime, long endTime, int problemCount, List<? extends Failure> failures) {
        super(startTime, endTime, problemCount);
        this.failures = failures;
    }

    @Override
    public List<? extends Failure> getFailures() {
        return failures;
    }
}
