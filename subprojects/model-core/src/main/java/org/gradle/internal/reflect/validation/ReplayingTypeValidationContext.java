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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class ReplayingTypeValidationContext implements TypeValidationContext {
    private final List<BiConsumer<String, TypeValidationContext>> problems = new ArrayList<>();

    @Override
    public void visitTypeProblem(Action<? super TypeProblemBuilder> problemSpec) {
        problems.add((ownerProperty, validationContext) -> validationContext.visitTypeProblem(problemSpec));
    }

    @Override
    public void visitPropertyProblem(Action<? super PropertyProblemBuilder> problemSpec) {
        problems.add((ownerProperty, validationContext) -> validationContext.visitPropertyProblem(builder -> {
            problemSpec.execute(builder);
            ((PropertyProblemBuilderInternal)builder).forOwner(ownerProperty);
        }));
    }

    public void replay(@Nullable String ownerProperty, TypeValidationContext target) {
        problems.forEach(problem -> problem.accept(ownerProperty, target));
    }
}
