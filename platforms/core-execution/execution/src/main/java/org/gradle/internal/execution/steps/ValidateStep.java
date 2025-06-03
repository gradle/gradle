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

package org.gradle.internal.execution.steps;

import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.internal.MutableReference;
import org.gradle.internal.execution.ExecutionProblemHandler;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.internal.snapshot.impl.UnknownImplementationSnapshot;
import org.gradle.util.internal.TextUtil;

import java.util.Collection;
import java.util.Objects;

import static org.gradle.api.problems.Severity.ERROR;
import static org.gradle.internal.deprecation.Documentation.userManual;

public class ValidateStep<C extends BeforeExecutionContext, R extends Result> implements Step<C, R> {
    private final ExecutionProblemHandler problemHandler;
    private final Step<? super ValidationFinishedContext, ? extends R> delegate;

    public ValidateStep(
        ExecutionProblemHandler problemHandler,
        Step<? super ValidationFinishedContext, ? extends R> delegate
    ) {
        this.problemHandler = problemHandler;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        WorkValidationContext validationContext = context.getValidationContext();
        work.validate(validationContext);
        work.checkOutputDependencies(validationContext);
        context.getBeforeExecutionState()
            .ifPresent(beforeExecutionState -> validateImplementations(work, beforeExecutionState, validationContext));

        problemHandler.handleReportedProblems(context.getIdentity(), work, validationContext);

        return delegate.execute(work, new ValidationFinishedContext(context, validationContext.getProblems()));
    }

    private static void validateImplementations(UnitOfWork work, BeforeExecutionState beforeExecutionState, WorkValidationContext validationContext) {
        MutableReference<Class<?>> workClass = MutableReference.empty();
        work.visitImplementations(new UnitOfWork.ImplementationVisitor() {
            @Override
            public void visitImplementation(Class<?> implementation) {
                workClass.set(GeneratedSubclasses.unpack(implementation));
            }

            @Override
            public void visitAdditionalImplementation(ImplementationSnapshot implementation) {
            }
        });
        // It doesn't matter whether we use cacheable true or false, since none of the warnings depends on the cacheability of the task.
        Class<?> workType = Objects.requireNonNull(workClass.get());
        TypeValidationContext workValidationContext = validationContext.forType(workType, true);
        validateImplementation(workValidationContext, beforeExecutionState.getImplementation(), "Implementation of ", work);
        beforeExecutionState.getAdditionalImplementations()
            .forEach(additionalImplementation -> validateImplementation(workValidationContext, additionalImplementation, "Additional action of ", work));
        beforeExecutionState.getInputProperties().forEach((propertyName, valueSnapshot) -> {
            if (valueSnapshot instanceof ImplementationSnapshot) {
                ImplementationSnapshot implementationSnapshot = (ImplementationSnapshot) valueSnapshot;
                validateNestedInput(workValidationContext, propertyName, implementationSnapshot);
            }
        });
    }

    private static final String UNKNOWN_IMPLEMENTATION_NESTED = "UNKNOWN_IMPLEMENTATION_NESTED";
    private static final String UNKNOWN_IMPLEMENTATION = "UNKNOWN_IMPLEMENTATION";

    private static void validateNestedInput(TypeValidationContext workValidationContext, String propertyName, ImplementationSnapshot implementation) {
        if (implementation instanceof UnknownImplementationSnapshot) {
            UnknownImplementationSnapshot unknownImplSnapshot = (UnknownImplementationSnapshot) implementation;
            workValidationContext.visitPropertyProblem(problem -> problem
                .forProperty(propertyName)
                .id(TextUtil.screamingSnakeToKebabCase(UNKNOWN_IMPLEMENTATION_NESTED), "Unknown property implementation", GradleCoreProblemGroup.validation().property())
                .contextualLabel(unknownImplSnapshot.getProblemDescription())
                .documentedAt(userManual("validation_problems", "implementation_unknown"))
                .details(unknownImplSnapshot.getReasonDescription())
                .solution(unknownImplSnapshot.getSolutionDescription())
                .severity(ERROR)
            );
        }
    }

    private static void validateImplementation(TypeValidationContext workValidationContext, ImplementationSnapshot implementation, String descriptionPrefix, UnitOfWork work) {
        if (implementation instanceof UnknownImplementationSnapshot) {
            UnknownImplementationSnapshot unknownImplSnapshot = (UnknownImplementationSnapshot) implementation;
            workValidationContext.visitPropertyProblem(problem -> problem
                .id(TextUtil.screamingSnakeToKebabCase(UNKNOWN_IMPLEMENTATION), "Unknown property implementation", GradleCoreProblemGroup.validation().property())
                .contextualLabel(descriptionPrefix + work + " " + unknownImplSnapshot.getProblemDescription())
                .documentedAt(userManual("validation_problems", "implementation_unknown"))
                .details(unknownImplSnapshot.getReasonDescription())
                .solution(unknownImplSnapshot.getSolutionDescription())
                .severity(ERROR)
            );
        }
    }

    @ServiceScope(Scope.Global.class)
    public interface ValidationWarningRecorder {
        void recordValidationWarnings(UnitOfWork.Identity identity, UnitOfWork work, Collection<? extends InternalProblem> warnings);
    }
}
