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
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.execution.Context;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.reflect.MessageFormattingTypeValidationContext;
import org.gradle.internal.reflect.TypeValidationContext;
import org.gradle.internal.reflect.TypeValidationContext.Severity;

import java.util.List;
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
            String displayName = context.getWork().getDisplayName();
            String errorMessage = errors.size() == 1
                ? String.format("A problem was found with the configuration of %s.", displayName)
                : String.format("Some problems were found with the configuration of %s.", displayName);
            List<InvalidUserDataException> causes = errors.stream()
                .limit(5)
                .sorted()
                .map(InvalidUserDataException::new)
                .collect(Collectors.toList());
            throw new WorkValidationException(errorMessage, causes);
        }
        return delegate.execute(context);
    }

    public interface ValidationWarningReporter {
        void reportValidationWarning(String warning);
    }

    private static class DefaultWorkValidationContext implements UnitOfWork.WorkValidationContext {
        private final ImmutableMultimap.Builder<Severity, String> problems = ImmutableMultimap.builder();

        @Override
        public TypeValidationContext createContextFor(Class<?> type, boolean cacheable) {
            return new MessageFormattingTypeValidationContext(null) {
                @Override
                protected void recordProblem(Severity severity, String message) {
                    if (severity == Severity.CACHEABLE_WARNING) {
                        if (!cacheable) {
                            return;
                        } else {
                            severity = Severity.WARNING;
                        }
                    }
                    problems.put(severity, message);
                }
            };
        }

        public ImmutableMultimap<Severity, String> getProblems() {
            return problems.build();
        }
    }
}
