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

import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.problems.BaseProblem;
import org.gradle.problems.Solution;

import java.util.List;
import java.util.function.Supplier;

public class TypeValidationProblem extends BaseProblem<ValidationProblemId, Severity, TypeValidationProblemLocation> {
    private final UserManualReference userManualReference;

    public TypeValidationProblem(ValidationProblemId id,
                                 Severity severity,
                                 TypeValidationProblemLocation where,
                                 Supplier<String> shortDescription,
                                 Supplier<String> longDescription,
                                 Supplier<String> reason,
                                 UserManualReference userManualReference,
                                 List<Supplier<Solution>> solutions) {
        super(id,
            severity,
            where,
            shortDescription,
            longDescription,
            reason,
            () -> userManualReference == null ? null : userManualReference.toDocumentationLink(),
            solutions);
        this.userManualReference = userManualReference;
    }

    public UserManualReference getUserManualReference() {
        return userManualReference;
    }
}
