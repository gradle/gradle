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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.logging.text.TreeFormatter;

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
            this.problems = ImmutableList.copyOf(problems);
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
}
