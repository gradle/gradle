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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@code WorkValidationException} is thrown when there is some validation problem with a work item.
 */
@Contextual
public class WorkValidationException extends GradleException {
    private static final int MAX_ERR_COUNT = Integer.getInteger("org.gradle.internal.max.validation.errors", 5);

    private final List<String> problems;

    private WorkValidationException(String message, Collection<String> problems) {
        super(message);
        this.problems = ImmutableList.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }

    public static Builder forProblems(Collection<String> problems) {
        return new Builder(problems);
    }

    public static class Builder {
        private final List<String> problems;
        private final int size;

        public Builder(Collection<String> problems) {
            this.problems = problems.stream().limit(MAX_ERR_COUNT).collect(ImmutableList.toImmutableList());
            this.size = problems.size();
        }

        public Builder limitTo(int maxProblems) {
            return new Builder(problems.stream().limit(maxProblems).collect(Collectors.toList()));
        }

        public BuilderWithSummary withSummary(Function<SummaryHelper, String> summaryBuilder) {
            return new BuilderWithSummary(problems, summaryBuilder.apply(new SummaryHelper()));
        }

        public class SummaryHelper  {
            public int size() {
                return size;
            }

            public String pluralize(String term) {
                if (size > 1) {
                    return term + "s";
                }
                return term;
            }
        }
    }

    public static class BuilderWithSummary {
        private final List<String> problems;
        private final String summary;

        public BuilderWithSummary(List<String> problems, String summary) {
            this.problems = problems;
            this.summary = summary;
        }

        public WorkValidationException get() {
            return build(null);
        }

        public WorkValidationException getWithExplanation(String explanation) {
            return build(explanation);
        }

        private WorkValidationException build(@Nullable String explanation) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(summary);
            formatter.startChildren();
            for (String problem : problems) {
                formatter.node(problem);
            }
            formatter.endChildren();
            if (explanation != null) {
                formatter.node(explanation);
            }
            return new WorkValidationException(formatter.toString(), ImmutableList.copyOf(problems));
        }
    }

    /**
     * This function can build a formatted String summarizing a list of problems with a
     * {@link UnitOfWork} or {@link Task}.
     */
    public static class ValidationErrorSummarizer implements Function<Builder.SummaryHelper, String> {
        private final String validatedObjectName;
        private final WorkValidationContext validationContext;

        public ValidationErrorSummarizer(UnitOfWork work, WorkValidationContext validationContext) {
            this.validatedObjectName = work.getDisplayName();
            this.validationContext = validationContext;
        }

        public ValidationErrorSummarizer(Task task, WorkValidationContext validationContext) {
            this.validatedObjectName = task.toString(); //String.format("task '%s'", task.getPath());
            this.validationContext = validationContext;
        }

        @Override
        public String apply(Builder.SummaryHelper helper) {
            return String.format("%s found with the configuration of %s (%s).",
                    helper.size() == 1 ? "A problem was" : "Some problems were",
                    validatedObjectName,
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
