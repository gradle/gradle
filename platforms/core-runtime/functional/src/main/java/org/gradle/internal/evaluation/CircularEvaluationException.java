/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.evaluation;

import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An exception caused by the circular evaluation.
 */
public class CircularEvaluationException extends GradleException {
    private final ImmutableList<EvaluationOwner> evaluationCycle;

    CircularEvaluationException(List<EvaluationOwner> evaluationCycle) {
        this.evaluationCycle = ImmutableList.copyOf(evaluationCycle);
    }

    @Override
    public String getMessage() {
        return "Circular evaluation detected: " + formatEvaluationChain(evaluationCycle);
    }

    /**
     * Returns the evaluation cycle.
     * The list represents a "stack" of owners currently being evaluated, and is at least two elements long.
     * The first and last elements of the list are the same owner.
     *
     * @return the evaluation cycle as a list
     */
    public List<EvaluationOwner> getEvaluationCycle() {
        return evaluationCycle;
    }

    private static String formatEvaluationChain(List<EvaluationOwner> evaluationCycle) {
        try (EvaluationScopeContext ignored = EvaluationContext.current().nested()) {
            return evaluationCycle.stream()
                .map(CircularEvaluationException::safeToString)
                .collect(Collectors.joining("\n -> "));
        }
    }

    /**
     * Computes {@code Object.toString()}, but swallows all thrown exceptions.
     */
    private static String safeToString(Object owner) {
        try {
            return owner.toString();
        } catch (Throwable e) {
            // Calling e.getMessage() here can cause infinite recursion.
            // It happens if e is CircularEvaluationException itself, because getMessage calls formatEvaluationChain.
            // It can also happen for some other custom exceptions that wrap CircularEvaluationException and call its getMessage inside their.
            // This is why we resort to losing the information and only providing exception class.
            // A well-behaved toString should not throw anyway.
            return owner.getClass().getName() + " (toString failed with " + e.getClass() + ")";
        }
    }
}
