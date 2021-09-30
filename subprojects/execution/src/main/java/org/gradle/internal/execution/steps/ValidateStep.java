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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.internal.MutableReference;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.reflect.validation.ValidationProblemBuilder;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.model.internal.type.ModelType;
import org.gradle.problems.BaseProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.convertToSingleLine;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderMinimalInformationAbout;

public class ValidateStep<C extends BeforeExecutionContext, R extends Result> implements Step<C, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateStep.class);
    private static final String MAX_NB_OF_ERRORS = "org.gradle.internal.max.validation.errors";

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

        Map<Severity, List<String>> problems = validationContext.getProblems()
            .stream()
            .collect(
                groupingBy(BaseProblem::getSeverity,
                mapping(ValidateStep::renderedMessage, toList())));
        ImmutableCollection<String> warnings = ImmutableList.copyOf(problems.getOrDefault(Severity.WARNING, ImmutableList.of()));
        ImmutableCollection<String> errors = ImmutableList.copyOf(problems.getOrDefault(Severity.ERROR, ImmutableList.of()));

        if (!warnings.isEmpty()) {
            warningReporter.recordValidationWarnings(work, warnings);
        }

        if (!errors.isEmpty()) {
            int maxErrCount = Integer.getInteger(MAX_NB_OF_ERRORS, 5);
            ImmutableSortedSet<String> uniqueSortedErrors = ImmutableSortedSet.copyOf(errors);
            throw WorkValidationException.forProblems(uniqueSortedErrors)
                .limitTo(maxErrCount)
                .withSummary(helper ->
                    String.format("%s found with the configuration of %s (%s).",
                        helper.size() == 1
                            ? "A problem was"
                            : "Some problems were",
                        work.getDisplayName(),
                        describeTypesChecked(validationContext.getValidatedTypes()))
                ).get();
        }

        if (!warnings.isEmpty()) {
            LOGGER.info("Invalidating VFS because {} failed validation", work.getDisplayName());
            virtualFileSystem.invalidateAll();
        }

        return delegate.execute(work, new ValidationFinishedContext() {
            @Override
            public Optional<BeforeExecutionState> getBeforeExecutionState() {
                return context.getBeforeExecutionState();
            }

            @Override
            public Optional<ValidationResult> getValidationProblems() {
                return warnings.isEmpty()
                    ? Optional.empty()
                    : Optional.of(() -> warnings);
            }

            @Override
            public Optional<PreviousExecutionState> getPreviousExecutionState() {
                return context.getPreviousExecutionState();
            }

            @Override
            public File getWorkspace() {
                return context.getWorkspace();
            }

            @Override
            public Optional<ExecutionHistoryStore> getHistory() {
                return context.getHistory();
            }

            @Override
            public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
                return context.getInputProperties();
            }

            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                return context.getInputFileProperties();
            }

            @Override
            public UnitOfWork.Identity getIdentity() {
                return context.getIdentity();
            }

            @Override
            public Optional<String> getNonIncrementalReason() {
                return context.getNonIncrementalReason();
            }

            @Override
            public WorkValidationContext getValidationContext() {
                return context.getValidationContext();
            }
        });
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
        validateImplementation(workValidationContext, beforeExecutionState.getImplementation(), "Implementation of ", work
        );
        beforeExecutionState.getAdditionalImplementations()
            .forEach(additionalImplementation -> validateImplementation(workValidationContext, additionalImplementation, "Additional action of ", work));
        beforeExecutionState.getInputProperties().forEach((propertyName, valueSnapshot) -> {
            if (valueSnapshot instanceof ImplementationSnapshot) {
                ImplementationSnapshot implementationSnapshot = (ImplementationSnapshot) valueSnapshot;
                validateNestedInput(workValidationContext, propertyName, implementationSnapshot);
            }
        });
    }

    private void validateNestedInput(TypeValidationContext workValidationContext, String propertyName, ImplementationSnapshot implementationSnapshot) {
        if (implementationSnapshot.isUnknown()) {
            workValidationContext.visitPropertyProblem(problem -> {
                ImplementationSnapshot.UnknownReason unknownReason = implementationSnapshot.getUnknownReason();
                configureImplementationValidationProblem(problem)
                    .forProperty(propertyName)
                    .withDescription(() -> unknownReason.descriptionFor(implementationSnapshot))
                    .happensBecause(unknownReason.getReason())
                    .addPossibleSolution(unknownReason.getSolution());
            });
        }
    }

    private void validateImplementation(TypeValidationContext workValidationContext, ImplementationSnapshot implementation, String descriptionPrefix, UnitOfWork work) {
        if (implementation.isUnknown()) {
            workValidationContext.visitPropertyProblem(problem -> {
                ImplementationSnapshot.UnknownReason unknownReason = implementation.getUnknownReason();
                configureImplementationValidationProblem(problem)
                    .withDescription(() -> descriptionPrefix + work + " " + unknownReason.descriptionFor(implementation))
                    .happensBecause(unknownReason.getReason())
                    .addPossibleSolution(unknownReason.getSolution());
            });
        }
    }

    private <T extends ValidationProblemBuilder<T>> T configureImplementationValidationProblem(ValidationProblemBuilder<T> problem) {
        return problem
            .typeIsIrrelevantInErrorMessage()
            .withId(ValidationProblemId.UNKNOWN_IMPLEMENTATION)
            .reportAs(Severity.WARNING)
            .documentedAt("validation_problems", "implementation_unknown");
    }

    private static String renderedMessage(org.gradle.internal.reflect.validation.TypeValidationProblem p) {
        if (p.getSeverity().isWarning()) {
            return convertToSingleLine(renderMinimalInformationAbout(p, true, false));
        }
        return renderMinimalInformationAbout(p);
    }

    private static String describeTypesChecked(ImmutableCollection<Class<?>> types) {
        return types.size() == 1
            ? "type '" + getTypeDisplayName(types.iterator().next()) + "'"
            : "types '" + types.stream().map(ValidateStep::getTypeDisplayName).collect(Collectors.joining("', '")) + "'";
    }

    private static String getTypeDisplayName(Class<?> type) {
        return ModelType.of(type).getDisplayName();
    }

    public interface ValidationWarningRecorder {
        void recordValidationWarnings(UnitOfWork work, Collection<String> warnings);
    }
}
