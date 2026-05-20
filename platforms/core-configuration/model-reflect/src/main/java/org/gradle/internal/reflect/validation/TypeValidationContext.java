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

public interface TypeValidationContext {

    /**
     * Visits a validation error associated with the given type.
     * Callers are encouraged to provide as much information as they can on
     * the problem following the problem builder instructions.
     *
     * @param problemSpec the problem builder
     */
    void visitTypeError(Action<? super TypeAwareProblemBuilder> problemSpec);

    /**
     * Visits a validation warning associated with the given type.
     * Callers are encouraged to provide as much information as they can on
     * the problem following the problem builder instructions.
     *
     * @param problemSpec the problem builder
     */
    void visitTypeWarning(Action<? super TypeAwareProblemBuilder> problemSpec);

    /**
     * Visits a validation error associated with the given property.
     * Callers are encouraged to provide as much information as they can on
     * the problem following the problem builder instructions.
     *
     * @param problemSpec the problem builder
     */
    void visitPropertyError(Action<? super TypeAwareProblemBuilder> problemSpec);

    /**
     * Visits a validation warning associated with the given property.
     * Callers are encouraged to provide as much information as they can on
     * the problem following the problem builder instructions.
     *
     * @param problemSpec the problem builder
     */
    void visitPropertyWarning(Action<? super TypeAwareProblemBuilder> problemSpec);

    TypeValidationContext NOOP = new TypeValidationContext() {
        @Override
        public void visitPropertyError(Action<? super TypeAwareProblemBuilder> problemSpec) {}

        @Override
        public void visitPropertyWarning(Action<? super TypeAwareProblemBuilder> problemSpec) {}

        @Override
        public void visitTypeError(Action<? super TypeAwareProblemBuilder> problemSpec) {}

        @Override
        public void visitTypeWarning(Action<? super TypeAwareProblemBuilder> problemSpec) {}
    };

}
