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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.reflect.TypeValidationContext.Severity;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.model.internal.type.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class ValidateStep<R extends Result> implements Step<AfterPreviousExecutionContext, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateStep.class);

    private final VirtualFileSystem virtualFileSystem;
    private final ValidationWarningRecorder warningReporter;
    private final Step<? super ValidationContext, ? extends R> delegate;

    public ValidateStep(
        VirtualFileSystem virtualFileSystem,
        ValidationWarningRecorder warningReporter,
        Step<? super ValidationContext, ? extends R> delegate
    ) {
        this.virtualFileSystem = virtualFileSystem;
        this.warningReporter = warningReporter;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, AfterPreviousExecutionContext context) {
        WorkValidationContext validationContext = context.getValidationContext();
        work.validate(validationContext);

        ImmutableMultimap<Severity, String> problems = validationContext.getProblems();
        ImmutableCollection<String> warnings = problems.get(Severity.WARNING);
        ImmutableCollection<String> errors = problems.get(Severity.ERROR);

        if (!warnings.isEmpty()) {
            warningReporter.recordValidationWarnings(work, warnings);
        }

        if (!errors.isEmpty()) {
            ImmutableSortedSet<String> uniqueSortedErrors = ImmutableSortedSet.copyOf(errors);
            throw new WorkValidationException(
                String.format("%s found with the configuration of %s (%s).",
                    uniqueSortedErrors.size() == 1
                        ? "A problem was"
                        : "Some problems were",
                    work.getDisplayName(),
                    describeTypesChecked(validationContext.getValidatedTypes())
                ),
                uniqueSortedErrors.stream()
                    .limit(5)
                    .map(InvalidUserDataException::new)
                    .collect(Collectors.toList())
            );
        }

        if (!warnings.isEmpty()) {
            LOGGER.info("Invalidating VFS because {} failed validation", work.getDisplayName());
            virtualFileSystem.invalidateAll();
        }

        return delegate.execute(work, new ValidationContext() {
            @Override
            public Optional<ValidationResult> getValidationProblems() {
                return warnings.isEmpty()
                    ? Optional.empty()
                    : Optional.of(() -> warnings);
            }

            @Override
            public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                return context.getAfterPreviousExecutionState();
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
            public Optional<String> getRebuildReason() {
                return context.getRebuildReason();
            }

            @Override
            public WorkValidationContext getValidationContext() {
                return context.getValidationContext();
            }
        });
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
