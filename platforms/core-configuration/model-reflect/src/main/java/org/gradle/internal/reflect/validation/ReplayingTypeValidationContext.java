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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class ReplayingTypeValidationContext implements TypeValidationContext {
    private final List<BiConsumer<String, TypeValidationContext>> warnings = new ArrayList<>();
    private final List<BiConsumer<String, TypeValidationContext>> errors = new ArrayList<>();

    @Override
    public void visitTypeError(Action<? super TypeAwareProblemBuilder> problemSpec) {
        errors.add((ownerProperty, validationContext) -> validationContext.visitTypeError(problemSpec));
    }

    @Override
    public void visitTypeWarning(Action<? super TypeAwareProblemBuilder> problemSpec) {
        warnings.add((ownerProperty, validationContext) -> validationContext.visitTypeWarning(problemSpec));
    }

    @Override
    public void visitPropertyError(Action<? super TypeAwareProblemBuilder> problemSpec) {
        errors.add((ownerProperty, validationContext) -> validationContext.visitPropertyError(builder -> {
            problemSpec.execute(builder);
            builder.parentProperty(ownerProperty);
        }));
    }

    @Override
    public void visitPropertyWarning(Action<? super TypeAwareProblemBuilder> problemSpec) {
        warnings.add((ownerProperty, validationContext) -> validationContext.visitPropertyWarning(builder -> {
            problemSpec.execute(builder);
            builder.parentProperty(ownerProperty);
        }));
    }

    public void replay(@Nullable String ownerProperty, TypeValidationContext target) {
        warnings.forEach(problem -> problem.accept(ownerProperty, target));
        errors.forEach(problem -> problem.accept(ownerProperty, target));
    }
}
