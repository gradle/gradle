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

import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationProblem;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This step exists to report any validation errors that exist prior to fingerprinting input during the {@link CaptureStateBeforeExecutionStep}.
 *
 * No new validations are run, but any existing validation errors present in the {@link WorkValidationContext} are reported as an
 * exception.
 *
 * @param <C> previous execution context
 * @param <R> result of execution delegate
 */
public class PreCaptureValidateStep<C extends PreviousExecutionContext, R extends CachingResult> extends AbstractValidateStep<C, R> {
    private final Step<? super PreviousExecutionContext, ? extends R> delegate;

    public PreCaptureValidateStep(BuildOperationExecutor buildOperationExecutor, Step<? super PreviousExecutionContext, ? extends R> delegate) {
        super(buildOperationExecutor);
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        WorkValidationContext validationContext = context.getValidationContext();
        List<TypeValidationProblem> validationErrors = context.getValidationContext().getProblems().stream()
                .filter(it -> it.getSeverity() == Severity.ERROR)
                .collect(Collectors.toList());
        if (!validationErrors.isEmpty()) {
            throwValidationException(work, validationContext, validationErrors);
        }
        return delegate.execute(work, context);
    }
}
