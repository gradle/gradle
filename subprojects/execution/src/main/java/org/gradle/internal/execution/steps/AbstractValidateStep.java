/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.validation.TypeValidationProblem;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Abstract class for steps that validate a {@link UnitOfWork} or report validation errors present in the {@link WorkValidationContext}.
 *
 * @param <C> previous execution context
 * @param <R> result of execution delegate
 */
/* package */ abstract class AbstractValidateStep<C extends Context, R extends Result> extends BuildOperationStep<C, R> {
    private static final int maxErrCount = Integer.getInteger("org.gradle.internal.max.validation.errors", 5);

    public AbstractValidateStep(BuildOperationExecutor buildOperationExecutor) {
        super(buildOperationExecutor);
    }

    protected void throwValidationException(UnitOfWork work, WorkValidationContext validationContext, Collection<TypeValidationProblem> validationErrors) {
        ImmutableSet<String> uniqueErrors = validationErrors.stream()
                .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
                .collect(ImmutableSet.toImmutableSet());
        throw WorkValidationException.forProblems(uniqueErrors)
                .limitTo(maxErrCount)
                .withSummary(new Summarizer(work, validationContext))
                .get();
    }

    private static class Summarizer implements Function<WorkValidationException.Builder.SummaryHelper, String> {
        private final UnitOfWork work;
        private final WorkValidationContext validationContext;

        public Summarizer(UnitOfWork work, WorkValidationContext validationContext) {
            this.work = work;
            this.validationContext = validationContext;
        }

        @Override
        public String apply(WorkValidationException.Builder.SummaryHelper helper) {
            return String.format("%s found with the configuration of %s (%s).",
                    helper.size() == 1 ? "A problem was" : "Some problems were",
                    work.getDisplayName(),
                    describeTypesChecked(validationContext.getValidatedTypes()));
        }

        private String describeTypesChecked(ImmutableCollection<Class<?>> types) {
            return types.size() == 1
                    ? "type '" + getTypeDisplayName(types.iterator().next()) + "'"
                    : "types '" + types.stream().map(this::getTypeDisplayName).collect(Collectors.joining("', '")) + "'";
        }

        private String getTypeDisplayName(Class<?> type) {
            return ModelType.of(type).getDisplayName();
        }
    }
}
