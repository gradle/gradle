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
import java.util.function.BiConsumer;

public interface TypeValidationContext {
    enum Severity {
        /**
         * A validation warning, emitted as a deprecation warning during runtime.
         */
        WARNING("Warning"),

        /**
         * A validation warning about cacheability issues, emitted as a deprecation warning during runtime.
         */
        CACHEABILITY_WARNING("Warning"),

        /**
         * A validation error, emitted as a failure cause during runtime.
         */
        ERROR("Error");

        private final String displayName;

        Severity(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Reduce options to {@link #WARNING} and {@link #ERROR}.
         */
        public Severity toReportableSeverity() {
            return this == CACHEABILITY_WARNING
                ? WARNING
                : this;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Visits a validation problem associated with the given type.
     */
    void visitTypeProblem(Severity severity, Class<?> type, String message);

    /**
     * Visits a validation problem associated with the given property of the validated type.
     */
    default void visitPropertyProblem(Severity severity, String message) {
        visitPropertyProblem(severity, null, null, message);
    }

    /**
     * Visits a validation problem associated with the given property of the validated type.
     */
    default void visitPropertyProblem(Severity severity, @Nullable String property, String message) {
        visitPropertyProblem(severity, null, property, message);
    }

    /**
     * Visits a validation problem associated with the given (child) property of the validated type.
     */
    void visitPropertyProblem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message);

    TypeValidationContext NOOP = new TypeValidationContext() {
        @Override
        public void visitTypeProblem(Severity severity, Class<?> type, String message) {}

        @Override
        public void visitPropertyProblem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message) {}
    };

    class ReplayingTypeValidationContext implements TypeValidationContext {
        private final List<BiConsumer<String, TypeValidationContext>> problems = new ArrayList<>();

        @Override
        public void visitTypeProblem(Severity severity, Class<?> type, String message) {
            problems.add((ownerProperty, validationContext) -> validationContext.visitTypeProblem(severity, type, message));
        }

        @Override
        public void visitPropertyProblem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message) {
            problems.add((ownerProperty, validationContext) -> validationContext.visitPropertyProblem(
                severity,
                combineParents(ownerProperty, parentProperty),
                property,
                message
            ));
        }

        public void replay(@Nullable String ownerProperty, TypeValidationContext target) {
            problems.forEach(problem -> problem.accept(ownerProperty, target));
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
