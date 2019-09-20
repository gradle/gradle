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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public interface TypeValidationContext {
    enum Severity {
        WARNING("Warning"), ERROR("Error");

        private final String displayName;

        Severity(String displayName) {
            this.displayName = displayName;
        }


        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Visits a validation problem associated with the given property.
     */
    default void visitProblem(Severity severity, String message) {
        visitProblem(severity, null, null, message);
    }

    /**
     * Visits a validation problem associated with the given property.
     */
    default void visitProblem(Severity severity, @Nullable String property, String message) {
        visitProblem(severity, null, property, message);
    }

    /**
     * Visits a validation problem associated with the given property.
     */
    void visitProblem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message);

    TypeValidationContext NOOP = (severity, parentProperty, property, message) -> {};

    class ReplayingTypeValidationContext implements TypeValidationContext {
        private static class Problem {
            private final Severity severity;
            private final String parentProperty;
            private final String property;
            private final String message;

            public Problem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message) {
                this.severity = severity;
                this.parentProperty = parentProperty;
                this.property = property;
                this.message = message;
            }
        }

        private final List<Problem> problems = new ArrayList<>();

        public ReplayingTypeValidationContext() {
        }

        @Override
        public void visitProblem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message) {
            problems.add(new Problem(severity, parentProperty, property, message));
        }

        public void replay(@Nullable String parentProperty, TypeValidationContext target) {
            problems.forEach(problem -> target.visitProblem(
                problem.severity,
                combineParents(parentProperty, problem.parentProperty),
                problem.property,
                problem.message
            ));
        }

        @Nullable
        private static String combineParents(@Nullable String grandParentProperty, @Nullable String parentProperty) {
            return grandParentProperty == null
                ? parentProperty
                : parentProperty == null
                    ? grandParentProperty
                    : grandParentProperty + "." + parentProperty;
        }
    }
}
