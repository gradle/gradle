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
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

/**
 * This class will validate a {@link LocalTaskNode}, logging any warnings discovered and halting
 * the build by throwing a {@link WorkValidationException} if any errors are found.
 */
public class DefaultNodeValidator implements NodeValidator {

    private final InternalProblems problemsService;

    public DefaultNodeValidator(InternalProblems problemsService) {
        this.problemsService = problemsService;
    }

    @Override
    public boolean hasValidationProblems(LocalTaskNode node) {
        WorkValidationContext validationContext = validateNode(node);
        List<? extends InternalProblem> problems = validationContext.getProblems();
        TaskInternal task = node.getTask();
        if (!problems.isEmpty()) {
            throwValidationException(problems, task, validationContext);
        }
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

    private void throwValidationException(List<? extends InternalProblem> problems, TaskInternal task, WorkValidationContext validationContext) {
        Set<String> uniqueErrors = getUniqueErrors(problems);
        WorkValidationException exception = WorkValidationException.forProblems(uniqueErrors)
            .withSummaryForContext(task.toString(), validationContext)
            .get();
        throw problemsService.getReporter().throwing(exception, problems);
    }

    private static Set<String> getUniqueErrors(List<? extends InternalProblem> problems) {
        return problems.stream()
            .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
            .collect(toImmutableSet());
    }
}
