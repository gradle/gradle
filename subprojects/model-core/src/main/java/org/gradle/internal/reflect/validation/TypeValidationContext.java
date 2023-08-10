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
     * Visits a validation problem associated with the given type.
     * Callers are encouraged to provide as much information as they can on
     * the problem following the problem builder instructions.
     *
     * @param problemSpec the problem builder
     */
    void visitTypeProblem(Action<? super TypeAwareProblemBuilder> problemSpec); // TODO (Reinhold) rename to visitTypeProblem

    /**
     * Visits a validation problem associated with the given property.
     * Callers are encouraged to provide as much information as they can on
     * the problem following the problem builder instructions.
     *
     * @param problemSpec the problem builder
     */
    void visitPropertyProblem(Action<? super TypeAwareProblemBuilder> problemSpec); // TODO (Reinhold) replace it with visitPropertyProblem(Action<? super TypeAwareProblemBuilder>)

    TypeValidationContext NOOP = new TypeValidationContext() {
        @Override
        public void visitPropertyProblem(Action<? super TypeAwareProblemBuilder> problemSpec) {}

        @Override
        public void visitTypeProblem(Action<? super TypeAwareProblemBuilder> problemSpec) { }
    };

}
