/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.reflect.validation;

import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.internal.reflect.problems.ValidationProblemId;

import java.util.function.Supplier;

@SuppressWarnings("UnusedReturnValue")
public interface ValidationProblemBuilder<T extends ValidationProblemBuilder<T>> {
    T withId(ValidationProblemId id);

    T withDescription(Supplier<String> message);

    T happensBecause(Supplier<String> message);

    default T happensBecause(String message) {
        return happensBecause(() -> message);
    }

    default T withDescription(String message) {
        return withDescription(() -> message);
    }

    T reportAs(Severity severity);

    T withLongDescription(Supplier<String> longDescription);

    default T withLongDescription(String longDescription) {
        return withLongDescription(() -> longDescription);
    }

    T documentedAt(String id, String section);

    T addPossibleSolution(Supplier<String> solution, Action<? super SolutionBuilder> solutionSpec);

    default T addPossibleSolution(Supplier<String> solution) {
        return addPossibleSolution(solution, Actions.doNothing());
    }

    default T addPossibleSolution(String solution) {
        return addPossibleSolution(() -> solution);
    }

    /**
     * Indicates that whenever this error is reported to the user,
     * it's not important, or even sometimes confusing, to report the type
     * on which it happened. This is the case for ad-hoc types (DefaultTask)
     * or, for example, when a problem happens because of ordering issues
     * and that it can be reported on multiple types.
     */
    T typeIsIrrelevantInErrorMessage();
}
