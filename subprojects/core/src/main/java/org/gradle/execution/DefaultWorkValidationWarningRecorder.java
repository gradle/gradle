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

import org.gradle.api.problems.Problem;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.steps.ValidateStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.joining;
import static org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour;
import static org.gradle.internal.deprecation.DeprecationMessageBuilder.withDocumentation;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.convertToSingleLine;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderMinimalInformationAbout;

public class DefaultWorkValidationWarningRecorder implements ValidateStep.ValidationWarningRecorder, WorkValidationWarningReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkValidationWarningRecorder.class);

    private final AtomicInteger workWithFailuresCount = new AtomicInteger();

    @Override
    public void recordValidationWarnings(UnitOfWork work, Collection<? extends Problem> warnings) {
        workWithFailuresCount.incrementAndGet();
        String uniqueWarnings = warnings.stream()
            .map(warning -> convertToSingleLine(renderMinimalInformationAbout(warning, true, false)))
            .map(warning -> "\n  - " + warning)
            .distinct()
            .collect(joining());
        LOGGER.warn("Execution optimizations have been disabled for {} to ensure correctness due to the following reasons:{}", work.getDisplayName(), uniqueWarnings);

        warnings.forEach(warning -> {
            withDocumentation(warning, deprecateBehaviour(convertToSingleLine(renderMinimalInformationAbout(warning, false, false)))
                    .withContext("Execution optimizations are disabled to ensure correctness.")
                    .willBeRemovedInGradle9())
                    .nagUser();
            }
        );
    }

    @Override
    public void reportWorkValidationWarningsAtEndOfBuild() {
        int workWithFailures = workWithFailuresCount.getAndSet(0);
        if (workWithFailures > 0) {
            LOGGER.warn(
                "\nExecution optimizations have been disabled for {} invalid unit(s) of work during this build to ensure correctness." +
                    "\nPlease consult deprecation warnings for more details.",
                workWithFailures
            );
        }
    }
}
