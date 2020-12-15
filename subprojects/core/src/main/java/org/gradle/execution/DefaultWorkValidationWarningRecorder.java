/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.steps.ValidateStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DefaultWorkValidationWarningRecorder implements ValidateStep.ValidationWarningRecorder, WorkValidationWarningReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkValidationWarningRecorder.class);

    private final AtomicInteger workWithFailuresCount = new AtomicInteger();

    @Override
    public void recordValidationWarnings(UnitOfWork work, Collection<String> warnings) {
        workWithFailuresCount.incrementAndGet();
        Set<String> uniqueSortedWarnings = ImmutableSortedSet.copyOf(warnings);
        LOGGER.warn("Validation failed for {}, disabling optimizations:{}",
            work.getDisplayName(),
            uniqueSortedWarnings.stream()
                .map(warning -> "\n  - " + warning)
                .collect(Collectors.joining()));
        uniqueSortedWarnings.forEach(warning -> DeprecationLogger.deprecateBehaviour(warning)
            .withContext("Execution optimizations are disabled due to the failed validation.")
            .willBeRemovedInGradle7()
            .withUserManual("more_about_tasks", "sec:up_to_date_checks")
            .nagUser());
    }

    @Override
    public void reportWorkValidationWarningsAtEndOfBuild() {
        int workWithFailures = workWithFailuresCount.getAndSet(0);
        if (workWithFailures > 0) {
            LOGGER.warn("\nExecution optimizations have been disabled for {} invalid unit(s) of work during the build. Consult deprecation warnings for more information.", workWithFailures);
        }
    }
}
