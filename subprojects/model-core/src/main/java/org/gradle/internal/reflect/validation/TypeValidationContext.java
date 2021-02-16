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

import javax.annotation.Nullable;

public interface TypeValidationContext {

    /**
     * Visits a validation problem associated with the given type.
     */
    void visitTypeProblem(Severity severity, Class<?> type, String message);

    /**
     * Visits a validation problem associated with the given type.
     * Callers are encourages to provide as much information as they can on
     * the problem following the problem builder instructions.
     * @param problemSpec the problem builder
     */
    void visitTypeProblem(Action<? super TypeProblemBuilder> problemSpec);

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

    /**
     * Visits a validation problem associated with the given property.
     * Callers are encourages to provide as much information as they can on
     * the problem following the problem builder instructions.
     * @param problemSpec the problem builder
     */
    void visitPropertyProblem(Action<? super PropertyProblemBuilder> problemSpec);

    TypeValidationContext NOOP = new TypeValidationContext() {
        @Override
        public void visitTypeProblem(Severity severity, Class<?> type, String message) {}

        @Override
        public void visitPropertyProblem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message) {}

        @Override
        public void visitTypeProblem(Action<? super TypeProblemBuilder> problemSpec) {}

        @Override
        public void visitPropertyProblem(Action<? super PropertyProblemBuilder> problemSpec) { }
    };

}
