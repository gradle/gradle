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
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.gradle.internal.RenderingUtils.oxfordJoin;
import static org.gradle.util.internal.TextUtil.getPluralEnding;

/**
 * A {@code WorkValidationException} is thrown when there is some validation problem with a work item.
 */
@Contextual
public class WorkValidationException extends GradleException {
    private static final String MAX_ERR_COUNT_PROPERTY = "org.gradle.internal.max.validation.errors";
    private static final int DEFAULT_MAX_ERR_COUNT = 5;

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

        public Builder limitTo(int maxProblems) {
            return new Builder(problems.stream().limit(maxProblems).collect(Collectors.toList()));
        }

        public BuilderWithSummary withSummaryForContext(String validatedObjectName, WorkValidationContext validationContext) {
            String summary = summarizeInContext(validatedObjectName, validationContext);
            return new BuilderWithSummary(problems, summary);
        }

        public BuilderWithSummary withSummaryForPlugin() {
            String summary = "Plugin validation failed with " + problems.size() + " problem" + getPluralEnding(problems);
            return new BuilderWithSummary(problems, summary);
        }

        private String summarizeInContext(String validatedObjectName, WorkValidationContext validationContext) {
            return String.format("%s found with the configuration of %s (%s).",
                    problems.size() == 1 ? "A problem was" : "Some problems were",
                    validatedObjectName,
                    describeTypesChecked(validationContext.getValidatedTypes()));
        }

        private String describeTypesChecked(ImmutableCollection<Class<?>> types) {
            return "type" + getPluralEnding(types) + " " + types.stream()
                .map(s -> "'" + this.getTypeDisplayName(s) + "'")
                .collect(oxfordJoin("and"));
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
}
