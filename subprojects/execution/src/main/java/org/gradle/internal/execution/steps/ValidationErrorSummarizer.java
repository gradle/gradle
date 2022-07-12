/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableCollection;
import org.gradle.api.Task;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException.Builder.SummaryHelper;
import org.gradle.model.internal.type.ModelType;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This function can build a formatted String summarizing a list of problems with a
 * {@link UnitOfWork} or {@link Task}.
 */
public class ValidationErrorSummarizer implements Function<SummaryHelper, String> {
    private final String validatedObjectName;
    private final WorkValidationContext validationContext;

    public ValidationErrorSummarizer(UnitOfWork work, WorkValidationContext validationContext) {
        this.validatedObjectName = work.getDisplayName();
        this.validationContext = validationContext;
    }

    public ValidationErrorSummarizer(Task task, WorkValidationContext validationContext) {
        this.validatedObjectName = String.format("task '%s'", task.getPath());
        this.validationContext = validationContext;
    }

    @Override
    public String apply(SummaryHelper helper) {
        return String.format("%s found with the configuration of %s (%s).",
                helper.size() == 1 ? "A problem was" : "Some problems were",
                validatedObjectName,
                describeTypesChecked(validationContext.getValidatedTypes()));
    }

    private String describeTypesChecked(ImmutableCollection<Class<?>> types) {
        return types.size() == 1
                ? "type '" + getTypeDisplayName(types.iterator().next()) + "'"
                : "types '" + types.stream().map(this::getTypeDisplayName).collect(Collectors.joining("', '")) + "'";
    }

    private String getTypeDisplayName(Class<?> type) {
        return ModelType.of(type).getDisplayName();
    }
}
