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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.reflect.MessageFormattingTypeValidationContext;
import org.gradle.internal.reflect.TypeValidationContext;
import org.gradle.internal.reflect.TypeValidationContext.Severity;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.model.internal.type.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;
import java.util.stream.Collectors;

public class ValidateStep<R extends Result> implements Step<AfterPreviousExecutionContext, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateStep.class);

    private final VirtualFileSystem virtualFileSystem;
    private final ValidateStep.ValidationWarningReporter warningReporter;
    private final Step<? super ValidationContext, ? extends R> delegate;

    public ValidateStep(
        VirtualFileSystem virtualFileSystem,
        ValidationWarningReporter warningReporter,
        Step<? super ValidationContext, ? extends R> delegate
    ) {
        this.virtualFileSystem = virtualFileSystem;
        this.warningReporter = warningReporter;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, AfterPreviousExecutionContext context) {
        DefaultWorkValidationContext validationContext = new DefaultWorkValidationContext();
        work.validate(validationContext);

        ImmutableMultimap<Severity, String> problems = validationContext.getProblems();
        ImmutableCollection<String> warnings = problems.get(Severity.WARNING);
        ImmutableCollection<String> errors = problems.get(Severity.ERROR);

        warnings.forEach(warningReporter::reportValidationWarning);

        if (!errors.isEmpty()) {
            throw new WorkValidationException(
                String.format("%s found with the configuration of %s (%s).",
                    errors.size() == 1
                        ? "A problem was"
                        : "Some problems were",
                    work.getDisplayName(),
                    describeTypesChecked(validationContext.getTypes())
                ),
                errors.stream()
                    .limit(5)
                    .sorted()
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
        });
    }

    private static String describeTypesChecked(ImmutableList<Class<?>> types) {
        return types.size() == 1
            ? "type '" + getTypeDisplayName(types.get(0)) + "'"
            : "types '" + types.stream().map(ValidateStep::getTypeDisplayName).collect(Collectors.joining("', '")) + "'";
    }

    private static String getTypeDisplayName(Class<?> type) {
        return ModelType.of(type).getDisplayName();
    }

    public interface ValidationWarningReporter {
        void reportValidationWarning(String warning);
    }

    private static class DefaultWorkValidationContext implements UnitOfWork.WorkValidationContext {
        private final ImmutableMultimap.Builder<Severity, String> problems = ImmutableMultimap.builder();
        private final ImmutableList.Builder<Class<?>> types = ImmutableList.builder();

        @Override
        public TypeValidationContext createContextFor(Class<?> type, boolean cacheable) {
            types.add(type);
            return new MessageFormattingTypeValidationContext(null) {
                @Override
                protected void recordProblem(Severity severity, String message) {
                    if (severity == Severity.CACHEABILITY_WARNING && !cacheable) {
                        return;
                    }
                    problems.put(severity.toReportableSeverity(), message);
                }
            };
        }

        public ImmutableMultimap<Severity, String> getProblems() {
            return problems.build();
        }

        public ImmutableList<Class<?>> getTypes() {
            return types.build();
        }
    }
}
