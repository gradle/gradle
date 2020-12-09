/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.internal.MutableBoolean;
import org.gradle.internal.reflect.TypeValidationContext;

import javax.annotation.Nullable;

public class DefaultNodeValidator implements NodeValidator {
    @Override
    public boolean hasValidationProblems(Node node) {
        if (node instanceof LocalTaskNode) {
            MutableBoolean foundProblem = new MutableBoolean();
            TypeValidationContext context = new TypeValidationContext() {
                @Override
                public void visitTypeProblem(Severity severity, Class<?> type, String message) {
                    foundProblem.set(true);
                }

                @Override
                public void visitPropertyProblem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message) {
                    foundProblem.set(true);
                }
            };
            ((LocalTaskNode) node).getTaskProperties().validateType(context);
            return foundProblem.get();
        } else {
            return false;
        }
    }
}
