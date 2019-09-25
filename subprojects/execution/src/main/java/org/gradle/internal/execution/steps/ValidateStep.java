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
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.execution.Context;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.reflect.MessageFormattingTypeValidationContext;
import org.gradle.internal.reflect.TypeValidationContext;
import org.gradle.internal.reflect.TypeValidationContext.Severity;
import org.gradle.model.internal.type.ModelType;

import java.util.stream.Collectors;

public class ValidateStep<C extends Context, R extends Result> implements Step<C, R> {
    private final ValidateStep.ValidationWarningReporter warningReporter;
    private final Step<? super C, ? extends R> delegate;

    public ValidateStep(
        ValidationWarningReporter warningReporter,
        Step<? super C, ? extends R> delegate
    ) {
        this.warningReporter = warningReporter;
        this.delegate = delegate;
    }

    @Override
    public R execute(C context) {
        DefaultWorkValidationContext validationContext = new DefaultWorkValidationContext();
        context.getWork().validate(validationContext);

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
                    context.getWork().getDisplayName(),
                    describeTypesChecked(validationContext.getTypes())
                ),
                errors.stream()
                    .limit(5)
                    .sorted()
                    .map(InvalidUserDataException::new)
                    .collect(Collectors.toList())
            );
        }
        return delegate.execute(context);
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
