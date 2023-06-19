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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.Severity;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.reflect.validation.TypeValidationProblem;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultTypeValidationContext extends ProblemRecordingTypeValidationContext {
    private final boolean reportCacheabilityProblems;
    private final ImmutableMap.Builder<String, Severity> problems = ImmutableMap.builder();

    public static DefaultTypeValidationContext withRootType(DocumentationRegistry documentationRegistry, Class<?> rootType, boolean cacheable) {
        return new DefaultTypeValidationContext(documentationRegistry, rootType, cacheable);
    }

    public static DefaultTypeValidationContext withoutRootType(DocumentationRegistry documentationRegistry, boolean reportCacheabilityProblems) {
        return new DefaultTypeValidationContext(documentationRegistry, null, reportCacheabilityProblems);
    }

    private DefaultTypeValidationContext(DocumentationRegistry documentationRegistry, @Nullable Class<?> rootType, boolean reportCacheabilityProblems) {
        super(documentationRegistry, rootType, Optional::empty);
        this.reportCacheabilityProblems = reportCacheabilityProblems;
    }

    @Override
    protected void recordProblem(TypeValidationProblem problem) {
        if (problem.getId().onlyAffectsCacheableWork() && !reportCacheabilityProblems) {
            return;
        }
        problems.put(TypeValidationProblemRenderer.renderMinimalInformationAbout(problem.toNewProblem()), problem.toNewProblem().getSeverity());
    }

    @Override
    protected void recordProblem(Problem problem) {
        if (/*problem.getId().onlyAffectsCacheableWork() &&*/ !reportCacheabilityProblems) { // TODO (donat) is is already fixed on master
            return;
        }
        problems.put(TypeValidationProblemRenderer.renderMinimalInformationAbout(problem), problem.getSeverity());
    }

    public ImmutableMap<String, Severity> getProblems() {
        return problems.build();
    }

    public static void throwOnProblemsOf(Class<?> implementation, ImmutableMap<String, org.gradle.api.problems.interfaces.Severity> validationMessages) {
        if (!validationMessages.isEmpty()) {
            String formatString = validationMessages.size() == 1
                ? "A problem was found with the configuration of %s."
                : "Some problems were found with the configuration of %s.";
            throw new DefaultMultiCauseException(
                String.format(formatString, ModelType.of(implementation).getDisplayName()),
                validationMessages.keySet().stream()
                    .sorted()
                    .map(InvalidUserDataException::new)
                    .collect(Collectors.toList())
            );
        }
    }

}
