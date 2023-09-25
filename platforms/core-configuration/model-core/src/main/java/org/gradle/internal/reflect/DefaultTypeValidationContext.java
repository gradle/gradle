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

package org.gradle.internal.reflect;

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemCategory;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class DefaultTypeValidationContext extends ProblemRecordingTypeValidationContext {

    public static final String MISSING_NORMALIZATION_ANNOTATION = "MISSING_NORMALIZATION_ANNOTATION";
    private final boolean reportCacheabilityProblems;
    private final ImmutableList.Builder<Problem> problems = ImmutableList.builder();

    public static DefaultTypeValidationContext withRootType(Class<?> rootType, boolean cacheable) {
        return new DefaultTypeValidationContext(rootType, cacheable);
    }

    public static DefaultTypeValidationContext withoutRootType(Problems problems, boolean reportCacheabilityProblems) {
        return new DefaultTypeValidationContext(null, reportCacheabilityProblems);
    }

    private DefaultTypeValidationContext(@Nullable Class<?> rootType, boolean reportCacheabilityProblems) {
        super(rootType, Optional::empty);
        this.reportCacheabilityProblems = reportCacheabilityProblems;
    }

    public static boolean onlyAffectsCacheableWork(String type) {
        return new ProblemCategory(MISSING_NORMALIZATION_ANNOTATION).toString().equals(type);
    }

    @Override
    protected void recordProblem(ReportableProblem problem) {
        if (onlyAffectsCacheableWork(problem.getProblemType()) && !reportCacheabilityProblems) { // TODO (donat) is is already fixed on master
            return;
        }
        problems.add(problem);
    }

    public ImmutableList<Problem> getProblems() {
        return problems.build();
    }

    public static void throwOnProblemsOf(Class<?> implementation, ImmutableList<Problem> validationMessages) {
        if (!validationMessages.isEmpty()) {
            String formatString = validationMessages.size() == 1
                ? "A problem was found with the configuration of %s."
                : "Some problems were found with the configuration of %s.";
            throw new DefaultMultiCauseException(
                String.format(formatString, ModelType.of(implementation).getDisplayName()),
                validationMessages.stream()
                    .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
                    .sorted()
                    .map(InvalidUserDataException::new)
                    .collect(toList())
            );
        }
    }

}
