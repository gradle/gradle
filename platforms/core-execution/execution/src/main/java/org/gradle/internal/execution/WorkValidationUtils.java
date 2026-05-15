/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.problems.internal.ProblemInternal;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour;
import static org.gradle.internal.deprecation.DeprecationMessageBuilder.withDocumentation;

public class WorkValidationUtils {

    private static final String MAX_ERR_COUNT_PROPERTY = "org.gradle.internal.max.validation.errors";
    private static final int DEFAULT_MAX_ERR_COUNT = 5;

    private WorkValidationUtils() {
    }

    /**
     * Deduplicates the given problems by {@code (id, contextual label, details, solutions, locations)}
     * and truncates the result at the cap configured via the
     * {@code org.gradle.internal.max.validation.errors} system property (default 5).
     */
    public static List<ProblemInternal> deduplicateAndTruncate(List<? extends ProblemInternal> problems) {
        return truncate(deduplicateByProperties(problems));
    }

    public static void reportAsDeprecation(Iterable<? extends ProblemInternal> problems) {
        problems.forEach(warning ->
            withDocumentation(warning, deprecateBehaviour(singleLineWarning(warning))
                .withContext("Execution optimizations are disabled to ensure correctness.")
                .willBecomeAnErrorInGradle10())
                .nagUser()
        );
    }

    private static String singleLineWarning(ProblemInternal problem) {
        String label = problem.getContextualLabel() != null
            ? problem.getContextualLabel()
            : problem.getDefinition().getId().getDisplayName();
        String details = problem.getDetails();
        return details != null ? label + ". Reason: " + details.replaceAll("\\s+", " ") : label;
    }

    private static <T> List<T> truncate(List<? extends T> problems) {
        return problems.stream()
            .limit(Integer.getInteger(MAX_ERR_COUNT_PROPERTY, DEFAULT_MAX_ERR_COUNT))
            .collect(toImmutableList());
    }

    private static List<ProblemInternal> deduplicateByProperties(List<? extends ProblemInternal> problems) {
        LinkedHashMap<List<Object>, ProblemInternal> unique = new LinkedHashMap<>();
        for (ProblemInternal problem : problems) {
            List<Object> key = Arrays.asList(
                problem.getDefinition().getId(),
                problem.getContextualLabel(),
                problem.getDetails(),
                problem.getSolutions(),
                problem.getOriginLocations()
            );
            unique.putIfAbsent(key, problem);
        }
        return ImmutableList.copyOf(unique.values());
    }
}
