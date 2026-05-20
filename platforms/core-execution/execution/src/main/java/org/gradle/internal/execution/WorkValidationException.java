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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

import static org.gradle.internal.RenderingUtils.oxfordJoin;

/**
 * A {@code WorkValidationException} is thrown when there is some validation problem with a work item.
 */
@Contextual
public class WorkValidationException extends GradleException implements ResolutionProvider {

    private final List<String> resolutions;

    private WorkValidationException(String message, List<String> resolutions) {
        super(message);
        this.resolutions = resolutions;
    }

    public static WorkValidationException withSummaryForPlugin(int problemCount, List<String> resolutions) {
        return new WorkValidationException(
            "Plugin validation failed with " + problemCount + " problem" + pluralEnding(problemCount),
            ImmutableList.copyOf(resolutions)
        );
    }

    public static WorkValidationException withSummaryForContext(String validatedObjectName, WorkValidationContext validationContext, int problemCount) {
        String summary = String.format("%s found with the configuration of %s (%s).",
            problemCount == 1 ? "A problem was" : "Some problems were",
            validatedObjectName,
            describeTypesChecked(validationContext.getValidatedTypes()));
        return new WorkValidationException(summary, ImmutableList.of());
    }

    public static WorkValidationException withSummaryForType(Class<?> implementation, int problemCount) {
        String summary = String.format("%s found with the configuration of %s.",
            problemCount == 1 ? "A problem was" : "Some problems were",
            ModelType.of(implementation).getDisplayName());
        return new WorkValidationException(summary, ImmutableList.of());
    }

    public static WorkValidationException withSummaryForTransformParameter(String parameterDisplayName, int problemCount) {
        String summary = String.format("%s found with the configuration of the artifact transform parameter %s.",
            problemCount == 1 ? "A problem was" : "Some problems were",
            parameterDisplayName);
        return new WorkValidationException(summary, ImmutableList.of());
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }

    private static String describeTypesChecked(ImmutableCollection<Class<?>> types) {
        return "type" + (types.size() > 1 ? "s" : "") + " " + types.stream()
            .map(s -> "'" + ModelType.of(s).getDisplayName() + "'")
            .collect(oxfordJoin("and"));
    }

    private static String pluralEnding(int count) {
        return count > 1 ? "s" : "";
    }
}
