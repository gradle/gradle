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
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

/**
 * A {@code WorkValidationException} is thrown when there is some validation problem with a work item.
 */
@Contextual
public class WorkValidationException extends GradleException implements ResolutionProvider {
    private static final String MAX_ERR_COUNT_PROPERTY = "org.gradle.internal.max.validation.errors";
    private static final int DEFAULT_MAX_ERR_COUNT = 5;

    private final List<String> problems;
    private final List<String> solutions;

    private WorkValidationException(String message, Collection<String> problems, Collection<String> solutions) {
        super(message);
        this.problems = ImmutableList.copyOf(problems);
        this.solutions = ImmutableList.copyOf(solutions);
    }

    public List<String> getProblems() {
        return problems;
    }

    public static Builder forProblems(Collection<String> problems) {
        return new Builder(problems);
    }

    @Override
    public List<String> getResolutions() {
        return solutions;
    }

    /**
     * A Stepwise builder for a {@code WorkValidationException}.
     *
     * This class represents step 1, call {@link #withSummaryForContext(String, WorkValidationContext)} or {@link #withSummaryForPlugin()} to return Step 2.
     */
    public final static class Builder {
        private final List<String> problems;

        public Builder(Collection<String> problems) {
            this.problems = problems.stream()
                    .limit(Integer.getInteger(MAX_ERR_COUNT_PROPERTY, DEFAULT_MAX_ERR_COUNT)) // Only retrieve the property upon building an error report
                    .collect(toImmutableList());
        }

        public BuilderWithSummary withSummaryForContext(String validatedObjectName, WorkValidationContext validationContext) {
            String summary = summarizeInContext(validatedObjectName, validationContext);
            return new BuilderWithSummary(problems, summary);
        }

        public BuilderWithSummary withSummaryForPlugin() {
            int size = problems.size();
            String summary = "Plugin validation failed with " + size + " problem" + (size == 1 ? "" : "s");
            return new BuilderWithSummary(problems, summary);
        }

        private String summarizeInContext(String validatedObjectName, WorkValidationContext validationContext) {
            return String.format("%s found with the configuration of %s (%s).",
                    problems.size() == 1 ? "A problem was" : "Some problems were",
                    validatedObjectName,
                    describeTypesChecked(validationContext.getValidatedTypes()));
        }

        private String describeTypesChecked(ImmutableCollection<Class<?>> types) {
            return types.size() == 1
                    ? "type '" + getTypeDisplayName(types.iterator().next()) + "'"
                    : "types '" + types.stream().map(this::getTypeDisplayName).collect(joining("', '")) + "'";
        }

        private String getTypeDisplayName(Class<?> type) {
                return ModelType.of(type).getDisplayName();
            }
    }

    /**
     * A builder for a {@code WorkValidationException} that has a summary attached.
     *
     * The {@link WorkValidationException#Builder} class is a Stepwise builder, this is step 2.
     */
    public final static class BuilderWithSummary {
        private final List<String> problems;
        private final String summary;
        private Collection<String> solutions = ImmutableList.of();

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

            problems.forEach(formatter::node);

            formatter.endChildren();
            if (explanation != null) {
                formatter.node(explanation);
            }
            return new WorkValidationException(formatter.toString(), problems, solutions);
        }

        public BuilderWithSummary withSolutions(Collection<String> solutions) {
            this.solutions = solutions;
            return this;
        }
    }
}
