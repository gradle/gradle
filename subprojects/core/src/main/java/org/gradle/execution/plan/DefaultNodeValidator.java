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

package org.gradle.execution.plan;

import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.problems.Problem;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static org.gradle.internal.deprecation.DeprecationMessageBuilder.withDocumentation;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.convertToSingleLine;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderMinimalInformationAbout;

/**
 * This class will validate a {@link LocalTaskNode}, logging any warnings discovered and halting
 * the build by throwing a {@link WorkValidationException} if any errors are found.
 */
public class DefaultNodeValidator implements NodeValidator {
    @Override
    public boolean hasValidationProblems(LocalTaskNode node) {
        WorkValidationContext validationContext = validateNode(node);
        List<? extends Problem> problems = validationContext.getProblems();
        logWarnings(problems);
        reportErrors(problems, node.getTask(), validationContext);
        return !problems.isEmpty();
    }

    private WorkValidationContext validateNode(LocalTaskNode node) {
        WorkValidationContext validationContext = node.getValidationContext();
        Class<?> taskType = GeneratedSubclasses.unpackType(node.getTask());
        // We don't know whether the task is cacheable or not, so we ignore cacheability problems for scheduling
        TypeValidationContext typeValidationContext = validationContext.forType(taskType, false);
        node.getTaskProperties().validateType(typeValidationContext);
        return validationContext;
    }

    private void logWarnings(List<? extends Problem> problems) {
        problems.stream()
            .filter(problem -> problem.getSeverity().isWarning())
            .forEach(problem -> {
                // Because our deprecation warning system doesn't support multiline strings (bummer!) both in rendering
                // **and** testing (no way to capture multiline deprecation warnings), we have to resort to removing details
                // and rendering
                String warning = convertToSingleLine(renderMinimalInformationAbout(problem, false, false));
                withDocumentation(problem, DeprecationLogger.deprecateBehaviour(warning)
                    .withContext("Execution optimizations are disabled to ensure correctness.")
                    .willBeRemovedInGradle9())
                    .nagUser();
            });
    }

    private void reportErrors(List<? extends Problem> problems, TaskInternal task, WorkValidationContext validationContext) {
        Set<String> uniqueErrors = getUniqueErrors(problems);
        if (!uniqueErrors.isEmpty()) {
            throw WorkValidationException.forProblems(uniqueErrors)
                .withSummaryForContext(task.toString(), validationContext)
                .get();
        }
    }

    private static Set<String> getUniqueErrors(List<? extends Problem> problems) {
        return problems.stream()
            .filter(problem -> !problem.getSeverity().isWarning())
            .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
            .collect(toImmutableSet());
    }
}
