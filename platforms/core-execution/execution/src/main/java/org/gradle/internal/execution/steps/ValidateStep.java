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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.InternalProblemReporter;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.MutableReference;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.internal.snapshot.impl.UnknownImplementationSnapshot;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.util.internal.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static org.gradle.api.problems.Severity.ERROR;
import static org.gradle.api.problems.Severity.WARNING;
import static org.gradle.internal.deprecation.Documentation.userManual;

public class ValidateStep<C extends BeforeExecutionContext, R extends Result> implements Step<C, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateStep.class);

    private final VirtualFileSystem virtualFileSystem;
    private final ValidationWarningRecorder warningReporter;
    private final Step<? super ValidationFinishedContext, ? extends R> delegate;

    public ValidateStep(
        VirtualFileSystem virtualFileSystem,
        ValidationWarningRecorder warningReporter,
        Step<? super ValidationFinishedContext, ? extends R> delegate
    ) {
        this.virtualFileSystem = virtualFileSystem;
        this.warningReporter = warningReporter;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        WorkValidationContext validationContext = context.getValidationContext();
        work.validate(validationContext);
        context.getBeforeExecutionState()
            .ifPresent(beforeExecutionState -> validateImplementations(work, beforeExecutionState, validationContext));

        InternalProblems problemsService = validationContext.getProblemsService();
        InternalProblemReporter reporter = problemsService.getInternalReporter();
        List<InternalProblem> problems = validationContext.getProblems();
        for (InternalProblem problem : problems) {
            reporter.report(problem);
        }

        Map<Severity, ImmutableList<InternalProblem>> problemsMap = problems.stream()
            .collect(
                groupingBy(p -> p.getDefinition().getSeverity(),
                    mapping(identity(), toImmutableList())));
        List<InternalProblem> warnings = problemsMap.getOrDefault(WARNING, ImmutableList.of());
        List<InternalProblem> errors = problemsMap.getOrDefault(ERROR, ImmutableList.of());

        if (!warnings.isEmpty()) {
            warningReporter.recordValidationWarnings(work, warnings);
        }

        if (!errors.isEmpty()) {
            throwValidationException(work, validationContext, errors);
        }

        if (!warnings.isEmpty()) {
            LOGGER.info("Invalidating VFS because {} failed validation", work.getDisplayName());
            virtualFileSystem.invalidateAll();
        }

        return delegate.execute(work, new ValidationFinishedContext(context, warnings));
    }

    private void validateImplementations(UnitOfWork work, BeforeExecutionState beforeExecutionState, WorkValidationContext validationContext) {
        MutableReference<Class<?>> workClass = MutableReference.empty();
        work.visitImplementations(new UnitOfWork.ImplementationVisitor() {
            @Override
            public void visitImplementation(Class<?> implementation) {
                workClass.set(GeneratedSubclasses.unpack(implementation));
            }

            @Override
            public void visitImplementation(ImplementationSnapshot implementation) {
            }
        });
        // It doesn't matter whether we use cacheable true or false, since none of the warnings depends on the cacheability of the task.
        Class<?> workType = workClass.get();
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

    private void validateNestedInput(TypeValidationContext workValidationContext, String propertyName, ImplementationSnapshot implementation) {
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

    private void validateImplementation(TypeValidationContext workValidationContext, ImplementationSnapshot implementation, String descriptionPrefix, UnitOfWork work) {
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

    protected void throwValidationException(UnitOfWork work, WorkValidationContext validationContext, Collection<? extends InternalProblem> validationErrors) {
        Set<String> uniqueErrors = validationErrors.stream()
            .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
            .collect(toImmutableSet());
        throw WorkValidationException.forProblems(uniqueErrors)
            .withSummaryForContext(work.getDisplayName(), validationContext)
            .get();
    }

    @ServiceScope(Scope.Global.class)
    public interface ValidationWarningRecorder {
        void recordValidationWarnings(UnitOfWork work, Collection<? extends InternalProblem> warnings);
    }
}
